(ns flipmunks.budget.validate)

(defn- validate
  [f & args]
  (if (apply f args)
    true
    (throw (ex-info "Validation failed" {:cause ::validation-error
                                         :data {:fn     f
                                                :params args}}))))

(defn- valid-user-tx? [user-tx]
  (let [required-fields #{:transaction/uuid
                          :transaction/name
                          :transaction/date
                          :transaction/amount
                          :transaction/currency
                          :transaction/created-at}]
    (validate every? #(contains? user-tx %) required-fields)))


(defn valid-user-txs?
  "Validate the user transactions to be posted. Verifies that the required attributes
  are included en every transaction, and throws an ExceptionInfo if validation fails."
  [user-txs]
  (validate every? #(valid-user-tx? %) user-txs))

(defn valid-signup?
  "Validate the signup parameters. Checks that username and password are not empty,
  and that the password matches the repeated password. Throws an ExceptionInfo if validation fails."
  [{:keys [request-method params]}]
  (let [{:keys [password username repeat]} params]
    (validate = request-method :post)
    (validate (every-pred not-empty) username password repeat)
    (validate = password repeat)))