(ns kibana-migration-tool.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :refer [join]]
            [clj-http.client :as client]
            [cheshire.core :refer :all])
  (:gen-class))

(defn kibana-docs
  "Fetches and returns all documents from `src` .kibana index."
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

(defn kibana-export
  "Exports all documents from `scr` .kibana index and saves them in
  specified `dst` directory."
  [src dst]
  (let [docs (kibana-docs src)]
    (save docs dst)))

(defn read-docs
  "Returns lazy sequence with all the documents in `src` directory."
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
       (join "\n")))

(defn upsert-docs
  "Updates or inserts provided documents into `dst` Elasticsearch instance."
  [dst docs]
  (let [body (to-bulk-update docs)
        rs (client/post (str dst "/_bulk")
                        {:body (str body "\n")
                         :content-type :json
                         :accept :json})]
    rs))

(defn kibana-import [src dst]
  (let [docs (read-docs src)]
    (upsert-docs dst docs)))

(defn -main []
  (println "It works!"))
