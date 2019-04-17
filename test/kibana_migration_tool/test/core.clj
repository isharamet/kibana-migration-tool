(ns kibana-migration-tool.test.core
  (:require [clojure.string :as s]
            [clojure.test :as test :refer :all]
            [kibana-migration-tool.core :as c]))

(deftest add-auth-test
  (let [rq {}]
    (testing "without auth"
      (let [auth nil
            expected rq
            actual (c/add-auth rq auth)]
        (is (= actual expected))))

    (testing "with unsupported auth type"
      (let [auth {:type :digest :auth "user:password"}
            expected rq
            actual (c/add-auth rq auth)]
        (is (= actual expected))))

    (testing "with supported auth type"
      (let [auth {:type :basic :auth "user:password"}
            expected (assoc rq :basic-auth "user:password")
            actual (c/add-auth rq auth)]
        (is (= actual expected))))))

(deftest export-request-test
  (let [export-rq {:query-params {:size 10000}
                   :body "{\"query\":{\"match_all\":{}}}"
                   :content-type :json
                   :accept :json}]
    (testing "without auth"
      (let [auth nil
            expected export-rq
            actual (c/export-request auth)]
        (is (= actual expected))))

    (testing "with auth"
      (let [auth {:type :basic :auth "user:password"}
            expected (assoc export-rq :basic-auth "user:password")
            actual (c/export-request auth)]
        (is (= actual expected))))))

(deftest import-request-test
  (let [import-rq {:body "\n"
                   :content-type :json
                   :accept :json}]
    (testing "without auth"
      (let [docs []
            auth nil
            expected import-rq
            actual (c/import-request docs auth)]
        (is (= actual expected))))

    (testing "with auth"
      (let [docs []
            auth {:type :basic :auth "user:password"}
            expected (assoc import-rq :basic-auth "user:password")
            actual (c/import-request docs auth)]
        (is (= actual expected))))))

(def bulk-update-lines
  ["{\"update\":{\"_id\":1,\"_type\":\"doc\",\"_index\":\".kibana\"}}"
   "{\"doc\":{\"a\":\"b\"},\"doc_as_upsert\":true}"
   "{\"update\":{\"_id\":2,\"_type\":\"doc\",\"_index\":\".kibana\"}}"
   "{\"doc\":{\"b\":\"c\"},\"doc_as_upsert\":true}"
   "{\"update\":{\"_id\":3,\"_type\":\"doc\",\"_index\":\".kibana\"}}"
   "{\"doc\":{\"c\":\"d\"},\"doc_as_upsert\":true}"])

(deftest to-bulk-update-test
  (testing "with empty docs"
    (let [docs []]
      (is (s/blank? (c/to-bulk-update docs)))))

  (testing "with non-empty docs"
    (let [docs [{:_id 1 :_source {:a :b}}
                {:_id 2 :_source {:b :c}}
                {:_id 3 :_source {:c :d}}]
          expected (s/join \newline bulk-update-lines)
          actual (c/to-bulk-update docs)]
      (is (= actual expected)))))

(deftest doc-type-test
  (testing "without doc type field"
    (let [doc {:a {:b :c}}]
      (is (nil? (c/doc-type doc)))))

  (testing "with doc type field"
    (let [doc {:_source {:type :type-a}}]
      (is (= (c/doc-type doc) :type-a)))))

(deftest dashboard-panels-test
  (testing "without panels"
    (let [doc {:a {:b :c}}]
      (is (nil? (c/dashboard-panels doc)))))

  (testing "with panels"
    (let [doc {:_source {:dashboard {:panelsJSON "{\"a\":\"b\"}"}}}
          expected {:a "b"}
          actual (c/dashboard-panels doc)]
      (is (= actual expected)))))

(deftest search-source-test
  (testing "without search source"
    (let [doc {:a {:b :c}}]
      (is (nil? (c/search-source :a doc)))))

  (testing "with search source"
    (let [doc {:_source
               {:a
                {:kibanaSavedObjectMeta
                 {:searchSourceJSON "{\"a\":\"b\"}"}}}}
          expected {:a "b"}
          actual (c/search-source :a doc)]
      (is (= actual expected)))))

