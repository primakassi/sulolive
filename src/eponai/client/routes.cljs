(ns eponai.client.routes
  (:require [bidi.bidi :as bidi]
            [eponai.client.ui.dashboard :refer [Dashboard ->Dashboard]]
            [eponai.client.ui.all_transactions :refer [AllTransactions ->AllTransactions]]
            [eponai.client.ui.settings :refer [Settings ->Settings]]
            [om.next :as om]))

(defprotocol RouteParamHandler
  (handle-route-params [this params reconciler]))

(defrecord UiComponentMatch [component factory route-param-fn]
  RouteParamHandler
  (handle-route-params [_ params reconciler]
    (route-param-fn params reconciler))
  bidi/Matched
  (resolve-handler [this m]
    (bidi/succeed this m))
  (unresolve-handler [this m] (when (= this (:handler m)) "")))

;; TODO: BUDGET INSTEAD OF TRANSACTIONS
(def routes
  (let [param-fn (fn [{:keys [budget-uuid]} reconciler]
                   (om/transact! reconciler `[(dashboard/set-active-budget {:budget-uuid ~(uuid budget-uuid)})]))
        dashboard (map->UiComponentMatch {:component      Dashboard
                                          :factory        ->Dashboard
                                          :route-param-fn param-fn})]
    ["/" {""             dashboard
          "dashboard/"   {""       dashboard
                          [:budget-uuid ""] dashboard}
          "transactions" (map->UiComponentMatch {:component AllTransactions
                                                 :factory ->AllTransactions})
          "settings"     (map->UiComponentMatch {:component Settings
                                                 :factory ->Settings})}]))
