(ns kibana-migration-tool.test.core
  (:require [clojure.test :as test :refer :all]
            [kibana-migration-tool.core :as c]))

(deftest add-auth-test
  (let [rq {}]
    (testing "without auth"
      (let [auth nil
            expected-rq rq
            actual-rq (c/add-auth rq auth)]
        (is (= actual-rq expected-rq))))

    (testing "with unsupported auth type"
      (let [auth {:type :digest :auth "user:password"}
            expected-rq rq
            actual-rq (c/add-auth rq auth)]
        (is (= actual-rq expected-rq))))
    
    (testing "with supported auth type"
      (let [auth {:type :basic :auth "user:password"}
            expected-rq (assoc rq :basic-auth "user:password")
            actual-rq (c/add-auth rq auth)]
        (is (= actual-rq expected-rq))))))

(deftest export-request-test
  (let [export-rq {:query-params {:size 10000}
                   :body "{\"query\":{\"match_all\":{}}}"
                   :content-type :json
                   :accept :json}]
    (testing "without auth"
      (let [auth nil
            expected-rq export-rq
            actual-rq (c/export-request auth)]
        (is (= actual-rq expected-rq))))

    (testing "with auth"
      (let [auth {:type :basic :auth "user:password"}
            expected-rq (assoc export-rq :basic-auth "user:password")
            actual-rq (c/export-request auth)]
        (is (= actual-rq expected-rq))))))

(deftest import-request-test
  (let [import-rq {:body "\n"
                   :content-type :json
                   :accept :json}]
    (testing "without auth"
      (let [docs []
            auth nil
            expected-rq import-rq
            actual-rq (c/import-request docs auth)]
        (is (= actual-rq expected-rq))))
    
    (testing "with auth"
      (let [docs []
            auth {:type :basic :auth "user:password"}
            expected-rq (assoc import-rq :basic-auth "user:password")
            actual-rq (c/import-request docs auth)]
        (is (= actual-rq expected-rq))))))
