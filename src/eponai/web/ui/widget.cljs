(ns eponai.web.ui.widget
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.d3.area-chart :refer [->AreaChart]]
    [eponai.web.ui.d3.bar-chart :refer [->BarChart]]
    [eponai.web.ui.d3.column-chart :refer [->ColumnChart]]
    [eponai.web.ui.d3.edge-bundling-graph :refer [->EdgeBundling]]
    [eponai.web.ui.d3.burndown-chart :refer [->BurndownChart]]
    [eponai.web.ui.d3.line-chart :refer [->LineChart]]
    [eponai.web.ui.d3.number-chart :refer [->NumberChart]]
    [eponai.web.ui.d3.progress-bar :refer [->ProgressBar]]
    [eponai.web.ui.daterangepicker :refer [->DateRangePicker]]
    [eponai.web.ui.dataset-filter-picker :refer [->DatasetFilterPicker]]
    [eponai.web.ui.tagfilterpicker :refer [->TagFilterPicker]]
    [eponai.web.routes :as routes]
    [eponai.web.ui.utils :as utils]
    [om.next :as om :refer-macros [defui]]
    [om.dom :as dom]
    [taoensso.timbre :refer-macros [debug]]
    [datascript.core :as d]
    [eponai.common.format.date :as date]))

(defn dimensions [graph]
  (let [style (:graph/style graph)]
    (cond (or
            (= style :graph.style/bar)
            (= style :graph.style/line)
            (= style :graph.style/area))
          {:maxH 3
           :minH 2
           :minW 50
           :maxW 100}

          :else
          {:minH 2
           :minW 25
           :maxW 100})))
(defui Widget
  static om/IQuery
  (query [_]
    '[:widget/uuid
     :widget/width
     :widget/height
     {:widget/filter [*
                      {:filter/include-tags [:tag/name]}
                      {:filter/exclude-tags [:tag/name]}
                      {:filter/end-date [:date/timestamp :date/ymd]}
                      {:filter/start-date [:date/timestamp :date/ymd]}]}
     {:widget/report [*
                      {:report/track [*
                                      {:track/functions [*]}]}
                      {:report/goal [*
                                     {:goal/cycle [*]}]}]}
     :widget/index
     :widget/data
     {:widget/graph [:graph/style
                     {:graph/filter [{:filter/include-tags [:tag/name]}
                                     {:filter/exclude-tags [:tag/name]}]}]}])

  Object
  (update-date-filter [this start end]
    (let [widget (om/props this)
          new-widget (update widget :widget/filter (fn [m]
                                                     (-> m
                                                         (assoc :filter/end-date (date/date-map end))
                                                         (assoc :filter/start-date (date/date-map start)))))]
      (om/transact! this `[(widget/edit ~(assoc (select-keys new-widget [:widget/filter :db/id :widget/uuid]) :mutation-uuid (d/squuid)))
                           :query/dashboard])))

  (update-data-set-filter [this filter]
    (let [widget (om/props this)
          new-widget (update widget :widget/filter merge filter)]
      (om/transact! this `[(widget/edit ~(assoc (select-keys new-widget [:widget/filter :db/id :widget/uuid]) :mutation-uuid (d/squuid)))
                           :query/dashboard
                           :query/transactions])))

  (update-tag-filter [this include-tags]
    (let [widget (om/props this)
          new-widget (assoc-in widget [:widget/graph :graph/filter :filter/include-tags] include-tags)]
      (om/transact! this `[(widget/edit ~(assoc (select-keys new-widget [:widget/graph :db/id :widget/uuid]) :mutation-uuid (d/squuid)))
                           :query/dashboard])))
  (delete-widget [this]
    (let [widget (om/props this)]
      (om/transact! this `[(widget/delete ~(assoc (select-keys widget [:widget/uuid]) :mutation-uuid (d/squuid)))
                           :query/dashboard])))

  (render [this]
    (let [{:keys [widget/report
                  widget/graph
                  widget/data] :as widget} (om/props this)
          {:keys [tag-filters-showing?]} (om/get-state this)
          {:keys [project-id
                  id
                  transactions]} (om/get-computed this)]

      (dom/div
        #js {:className "widget"}
        (dom/header
          #js {:className "widget-header"}

          ;; Widget title
          (dom/div
            #js {:className "widget-title"}
            (dom/input
              #js {:value (or (:report/title report) "")
                   :type "text"}))

          ;; Widget menu
          (when (some? (:db/id widget))
            (dom/div
              #js {:className "widget-menu float-right menu-horizontal"}

              (->DatasetFilterPicker (om/computed {:key     "dataset-filter-picker"
                                                   :filters (:widget/filter widget)}
                                                  {:on-change #(.update-data-set-filter this %)}))

              (->TagFilterPicker (om/computed {:key          "tag-filter-picker"
                                               :transactions transactions
                                               :filters      (:graph/filter graph)}
                                              {:on-apply #(.update-tag-filter this %)}))

              (->DateRangePicker (om/computed {:key   "date-range-picker"
                                               :class "nav-link"}
                                              {:on-apply  #(.update-date-filter this %1 %2)
                                               :on-cancel #()}))

              ;; Widget edit navigation
              ;(dom/a
              ;  #js {:className "nav-link widget-edit secondary"
              ;       :href (when project-id
              ;               (routes/key->route :route/project->widget+type+id
              ;                                  {:route-param/project-id  project-id
              ;                                   :route-param/widget-type (if (:report/track report) :track :goal)
              ;                                   :route-param/widget-id   (str (:db/id widget))}))}
              ;  (dom/i
              ;    #js {:className "fa fa-fw fa-pencil"}))
              (dom/a
                #js {:className "nav-link secondary"
                     :onClick   #(.delete-widget this)}
                (dom/i
                  #js {:className "fa fa-fw fa-trash-o"}))

              ;; Move widget handle
              (dom/a
                #js {:className "nav-link widget-move secondary"}
                (dom/i
                  #js {:className "fa fa-fw fa-arrows widget-move"}))


              ;; Widget submenu
              ;(dom/a
              ;  #js {:className "nav-link secondary"}
              ;  (dom/i
              ;    #js {:className "fa fa-fw fa-ellipsis-v"}))
              )))


        (dom/div
          #js {:className "widget-data"}
          (let [{:keys [graph/style]} graph
                settings {:data         data
                          :id           (str (or (:widget/uuid widget) id "widget"))
                          ;; Pass the widget to make this component re-render
                          ;; if the widget data changes.
                          :widget       widget
                          ;:width        "100%"
                          ;:height       "100%"
                          :title-axis-y "Amount ($)"}]

            (cond (= style :graph.style/bar)
                  (->ColumnChart settings)

                  (= style :graph.style/area)
                  (->AreaChart settings)

                  (= style :graph.style/number)
                  (->NumberChart settings)

                  (= style :graph.style/line)
                  (->LineChart settings)

                  (= style :graph.style/progress-bar)
                  (->ProgressBar settings)

                  (= style :graph.style/burndown)
                  (->BurndownChart settings)

                  (= style :graph.style/chord)
                  (->EdgeBundling settings)
                  ;[:div
                  ; "Progress: "
                  ; [:div.progress
                  ;  {:aria-valuenow  "50"
                  ;   :aria-valuemin  "0"
                  ;   :aria-valuetext "$10"
                  ;   :aria-valuemax  "100"}
                  ;  [:div.progress-meter
                  ;   (opts {:style {:width "50%"}})]]]
                  )))))))

(def ->Widget (om/factory Widget))