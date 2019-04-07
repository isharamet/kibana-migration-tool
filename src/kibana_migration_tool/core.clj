(ns kibana-migration-tool.core
  (:require [clojure.java.io :as io]
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
  (keyword (get-in doc [:_source :type])))

(defn save
  "Saves collection of documents in `dst` grouping them by type."
  [docs dst]
  (doseq [[t ds] (group-by doc-type docs)
          d ds]
    (let [p (io/file (str dst "/" t))
          id (:_id d)
          f (str p "/" id ".edn")]
      (do (.mkdirs p)
          (spit f d)))))

(defn kibana-export
  "Exports all documents from `scr` .kibana index and saves them in
  specified `dst` location."
  [src dst]
  (let [docs (get-docs src)]
    (save docs dst)))

(defn kibana-import [src dst]
  ())

(kibana-export "http://localhost:9200" "./backup")

(defn -main []
  (println kibana))
