(ns kibana-migration-tool.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :refer [join]]
            [clojure.tools.cli :refer [parse-opts]]
            [clj-http.client :as client]
            [cheshire.core :refer :all])
  (:gen-class))

(defn read-es-docs
  "Fetches and returns collection of all documents from `src` Elasticsearch instance."
  [src]
  (let [rs (client/get (str src "/.kibana/_search")
                       {:query-params {:size 10000}
                        :body (generate-string {:query {:match_all {}}})
                        :content-type :json
                        :accept :json})
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
          fname (str path "/" id ".edn")]
      (do (.mkdirs path)
          (spit fname doc)))))

(defn read-fs-docs
  "Reads and returns lazy collection of all the documents from `src` directory."
  [src]
  (->> (io/file src)
       (file-seq)
       (filter #(.isFile %))
       (map slurp)
       (map edn/read-string)))

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

(defn upsert-docs
  "Updates or inserts provided documents into `dst` Elasticsearch instance."
  [dst docs]
  (client/post (str dst "/_bulk")
               {:body (str (to-bulk-update docs) \newline)
                :content-type :json
                :accept :json}))

(defn kibana-export
  "Exports all documents from `src` .kibana index and saves them in
  specified `dst` directory."
  [{:keys [source destination]}]
  (let [docs (read-es-docs source)]
    (save docs destination)))

(defn kibana-import
  "Imports all documents in `src` folder into `dst` Elasticsearch instance."
  [{:keys [source destination]}]
  (let [docs (read-fs-docs source)]
    (upsert-docs destination docs)))

(def cli-options
  [["-s" "--source SRC"]
   ["-d" "--destination DST"]
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
        "  export   Start a new server"
        "  import   Stop an existing server"
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
       (case action
         "export" (kibana-export options)
         "import" (kibana-import options)))))
