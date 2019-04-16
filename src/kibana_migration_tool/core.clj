(ns kibana-migration-tool.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :refer [join split]]
            [clojure.set :refer [union]]
            [clojure.tools.cli :refer [parse-opts]]
            [clj-http.client :as client]
            [cheshire.core :refer :all])
  (:gen-class))

(defn add-auth
  "Adds authentication to the request if `auth` is a valid authentication object."
  [rq auth]
  (if (nil? auth)
    rq
    (let [{:keys [type auth]} auth]
      (case type
        :basic
        (merge rq {:basic-auth auth})

        rq))))

(defn export-request
  "Creates export request with given authentication method if `auth` is a vaild
  authentication object."
  [auth]
  (let [rq {:query-params {:size 10000}
            :body (generate-string {:query {:match_all {}}})
            :content-type :json
            :accept :json}]
    (add-auth rq auth)))

(defn to-bulk-update
  "Transforms document collection to Elasticsearch bulk update body."
  [docs]
  (->> docs
       (mapcat (fn [doc]
                 [{:update {:_id (:_id doc)
                            :_type "doc"
                            :_index ".kibana"}}
                  {:doc (:_source doc)
                   :doc_as_upsert true}]))
       (map generate-string)
       (join \newline)))

(defn import-request
  "Creates import request from `docs` sequence with given authentication method if
  `auth` is a valid authentication object."
  [docs auth]
  (let [rq {:body (str (to-bulk-update docs) \newline)
            :content-type :json
            :accept :json}]
    (add-auth rq auth)))

(defn read-es-docs
  "Fetches and returns collection of all documents from `src` Elasticsearch instance."
  [src auth]
  (let [rs (client/get (str src "/.kibana/_search/")
                       (export-request auth))
        body (parse-string (:body rs) true)
        hits (get-in body [:hits :hits])]
    hits))

(defn doc-type
  "Extracts document type (i.e. dashboard/visualization/config etc.)."
  [doc]
  (get-in doc [:_source :type]))

(defn save
  "Saves collection of documents in `dst` directory grouping them by type."
  [docs dst]
  (doseq [[type docs] (group-by doc-type docs)
          doc docs]
    (let [path (io/file (str dst "/" type))
          id (:_id doc)
          fname (str path "/" id ".json")]
      (do (.mkdirs path)
          (spit fname (generate-string doc {:pretty true}))))))

(defn read-fs-docs
  "Reads and returns lazy collection of all the documents from `src` directory."
  [src]
  (->> (io/file src)
       (file-seq)
       (filter #(.isFile %))
       (map slurp)
       (map #(parse-string % true))))

(defn upsert-docs
  "Updates or inserts provided documents into `dst` Elasticsearch instance."
  [dst docs & {:keys [auth]}]
  (client/post (str dst "/_bulk")
               (import-request docs auth)))

(defn dashboard-panels
  "Returns collection of maps with panel information for provided dashboard."
  [doc]
  (-> doc
      (get-in [:_source :dashboard :panelsJSON])
      (parse-string true)))

(defn search-source
  "Returns search source map for provided object."
  [type doc]
  (-> doc
      (get-in [:_source type :kibanaSavedObjectMeta :searchSourceJSON])
      (parse-string true)))

(defn find-by-id
  "Finds document by id in provided collection of documents."
  [id docs]
  (first (filter (fn [e] (= (:_id e) id)) docs)))

(defn extract-dependencies [doc]
  "Extracts all dependencies from given document."
  (let [type (keyword (doc-type doc))
        result #{}]
    (case type
      :dashboard
      (->> doc
           (dashboard-panels)
           (map (fn [e] (str (:type e) ":" (:id e))))
           (set))

      (:visualization :search)
      (let [source (search-source type doc)]
        (if (empty? source)
          result
          #{(str "index-pattern:" (:index source))}))

      result)))

(defn find-dependencies
  "Recursively traces all dependencies for the provided document in the
  given list of documents and returns set of dependent document ids inclding
  the id of initial document."
  [doc docs]
  (loop [xs [doc]
         result #{(:_id doc)}]
    (if (empty? xs)
      result
      (let [ds (reduce union (map extract-dependencies xs))
            ddocs (map #(find-by-id % docs) ds)]
        (recur ddocs (union result ds))))))

(defn doc-filter [objs docs]
  (let [ids (->> objs
                 (map #(find-by-id % docs))
                 (map #(find-dependencies % docs))
                 (reduce union))]
    (if (empty? ids)
      (fn [doc] true)
      (fn [doc] (contains? ids (:_id doc))))))

(defn kibana-export
  "Exports all documents (or ones listed in `objs`) from `src` .kibana index
  and saves them in specified `dst` directory."
  [src dst & {:keys [objs auth]}]
  (let [docs (read-es-docs src auth)
        f (doc-filter objs docs)]
    (save (filter f docs) dst)))

(defn kibana-import
  "Imports all documents  (or ones listed in `objs`) in `src` folder into
  `dst` Elasticsearch instance."
  [src dst & {:keys [objs auth]}]
  (let [docs (read-fs-docs src)
        f (doc-filter objs docs)]
    (upsert-docs dst (filter f docs) :auth auth)))

(def cli-options
  [["-s" "--source SOURCE"
    "Source: Elasticsearch URI for export, path for import"
    :id :src]
   ["-d" "--destination DESTINATION"
    "Destination: path for export, Elasticsearch URI for import"
    :id :dst]
   ["-o" "--objects OBJECTS"
    "Objects: comma-separated list of saved object IDs to export/import"
    :id :objs
    :default []
    :default-desc ""
    :parse-fn #(split % #",")]
   ["-a" "--auth AUTH"
    "Authentication: `username:password` - for basic auth"
    :id :auth
    :default nil
    :default-desc ""]
   ["-A" "--auth-type AUTH-TYPE"
    "Authentication type: supported types: `basic`"
    :id :auth-type
    :default :basic
    :default-desc "basic"
    :parse-fn keyword]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Kibana Migration Tool."
        ""
        "Usage: kmt action [options]"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  export   Export saved objects"
        "  import   Import saved objects"
        ""]
       (join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options)
      {:exit-message (usage summary) :ok? true}

      errors
      {:exit-message (error-msg errors)}

      (and (= 1 (count arguments))
           (#{"export" "import"} (first arguments)))
      {:action (first arguments) :options options}

      :else
      {:exit-message (usage summary)})))

(defn -main [& args]
  (let [{:keys [action options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (let [{:keys [src dst objs auth auth-type]} options
            auth (if (nil? auth)
                   nil
                   {:type auth-type :auth auth})]
        (case action
          "export" (kibana-export src dst :objs objs :auth auth)
          "import" (kibana-import src dst :objs objs :auth auth))))))

