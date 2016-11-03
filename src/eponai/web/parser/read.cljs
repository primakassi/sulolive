(ns eponai.web.parser.read
  (:require [datascript.core :as d]
            [om.next :as om]
            [eponai.client.parser.read :as read]
            [eponai.common.parser :refer [client-read]]
            [eponai.common.parser.util :as p.util]
            [eponai.common.database.pull :as p]
            [taoensso.timbre :refer-macros [debug]]))

;; ################ Local reads ####################
;; Local reads goes here. These are specific to the
;; app running on this platform.
;; Remote reads should be defined in:
;;     eponai.client.parser.read

;; -------- Readers for UI components

(defmethod client-read :routing/project
  [{:keys [db] :as env} k p]
  (let [union-key (:ui.component.project/selected-tab (d/entity db [:ui/component :ui.component/project]))]
    (p.util/read-union env k p union-key)))

(defmethod client-read :routing/app-root
  [{:keys [db] :as env} k p]
  (let [handler (:ui.component.root/route-handler (d/entity db [:ui/component :ui.component/root]))
        union-key (or (:route-key handler) :route/project)]
    (p.util/read-union env k p union-key)))

(defmethod client-read :routing/navbar-settings
  [{:keys [db] :as env} k p]
  (p.util/read-union env k p (if (:ui.component.navbar/settings-open?
                                   (d/entity db [:ui/component :ui.component/navbar]))
                               :settings
                               :nothing)))

;; TODO: A lot of target boilerplate here. Macro instead?
(defmethod client-read :query/active-project
  [{:keys [db _ target]} _ _]
  (when-not target
    {:value
     (let [project-eid (read/active-project-eid db)
           project-entity (when project-eid (d/entity db project-eid))]
       (cond-> (into {} (d/entity db [:ui/component :ui.component/project]))
               (seq project-entity)
               (assoc :ui.component.project/active-project project-entity)))}))

(defmethod client-read :query/selected-transaction
  [{:keys [db query target]} _ _]
  (when-not target
    (read/read-entity-by-key db query [:ui/component :ui.component/transactions])))

(defmethod client-read :query/active-widget-open
  [{:keys [db query target]} _ _]
  (when-not target
    {:value (p/pull db query [:ui/component :ui.component/widget])}))

(defmethod client-read :query/active-widget
  [{:keys [db query target]} _ _]
  (when-not target
    (let [{:keys [ui.component.widget/id]} (p/lookup-entity db [:ui/component :ui.component/widget])]
     {:value (when (number? id)
               (p/pull db query id))})))

(defmethod client-read :query/widget-type
  [{:keys [db query target]} _ _]
  (when-not target
    {:value (p/pull db query [:ui/component :ui.component/widget])}))

(defmethod client-read :query/root-component
  [{:keys [db query target]} _ _]
  (when-not target
    (read/read-entity-by-key db nil [:ui/component :ui.component/root])))

(defmethod client-read :query/sidebar
  [{:keys [db query target]} _ _]
  (when-not target
    (read/read-entity-by-key db query [:ui/component :ui.component/sidebar])))

(defmethod client-read :query/navbar
  [{:keys [db query target]} _ _]
  (when-not target
    (read/read-entity-by-key db query [:ui/component :ui.component/navbar])))