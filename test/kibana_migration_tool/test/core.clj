(ns kibana-migration-tool.test.core
  (:require [clojure.test :as test :refer :all]
            [kibana-migration-tool.core :as c]))

(deftest export-request-test
  (let [export-rq {:query-params {:size 10000}
                   :body "{\"query\":{\"match_all\":{}}}"
                   :content-type :json
                   :accept :json}]
    (testing "without auth"
      (let [expected-rq export-rq
            actual-rq (c/export-request nil)]
        (is (= actual-rq expected-rq))))

    (testing "with auth"
      (let [auth {:type :basic :auth "user:password"}
            expected-rq (assoc export-rq :basic-auth "user:password")
            actual-rq (c/export-request auth)]
        (is (= actual-rq expected-rq))))))

