(ns eponai.common.ui.store.store-dashboard
  (:require
    [eponai.client.parser.message :as msg]
    [eponai.common.ui.navbar :as nav]
    [eponai.common.ui.product-item :as pi]
    [eponai.common.ui.elements.css :as css]
    [eponai.common.ui.common :as common]
    [eponai.common.ui.product :as item]
    [eponai.common.ui.stream :as stream]
    #?(:cljs [eponai.web.utils :as utils])
    [eponai.common.ui.dom :as my-dom]
    [eponai.common.format :as format]
    [om.dom :as dom]
    [om.next :as om #?(:clj :refer :cljs :refer-macros) [defui]]
    [taoensso.timbre :refer [debug]]
    [eponai.common.ui.elements.photo :as photo]
    [eponai.common.ui.elements.menu :as menu]
    [eponai.common :as c]))

(def form-elements
  {:input-price      "input-price"
   :input-on-sale?   "input-on-sale?"
   :input-sale-price "input-sale-price"
   :input-name       "input-name"})

(defn route-store [store-id & [path]]
  (str "/store/" store-id "/dashboard" path))

(defn store-orders [component store opts]
  (my-dom/div (->> (css/grid-row)
                   css/grid-column)
              (dom/div nil "Thes are the orders")))

#?(:cljs (defn get-element [id]
           (.getElementById js/document id)))

(defn product-edit-form [component & [product]]
  (let [{:store.item/keys [price]
         item-name        :store.item/name} product
        update-resp (msg/one-message component 'stripe/update-product)
        create-resp (msg/one-message component 'stripe/create-product)
        is-loading? (or (msg/pending? update-resp) (msg/pending? create-resp))]
    (dom/div nil
      (when is-loading?
        (common/loading-spinner nil))
      (my-dom/div (->> (css/grid-row)
                       (css/grid-column))
                  (dom/h4 nil "Edit Product"))
      (my-dom/div (->> (css/grid-row)
                       (css/grid-column))
                  (dom/label nil "Name:")
                  (my-dom/input {:id           (get form-elements :input-name)
                                 :type         "text"
                                 :defaultValue (or item-name "")}))
      (my-dom/div (->> (css/grid-row))
                  (my-dom/div (css/grid-column)
                              (dom/label nil "Price:")
                              (my-dom/input {:id           (get form-elements :input-price)
                                             :type         "number"
                                             :defaultValue (or price "0")}))
                  (my-dom/div (css/grid-column)
                              (dom/label nil "On Sale")
                              (my-dom/input {:id   (get form-elements :input-on-sale?)
                                             :type "checkbox"}))
                  (my-dom/div (css/grid-column)
                              (dom/label nil "Sale Price:")
                              (my-dom/input {:id           (get form-elements :input-sale-price)
                                             :className    "disabled"
                                             :type         "number"
                                             :disabled     true
                                             :defaultValue (or price "")})))
      (my-dom/div (->> (css/grid-row)
                       (css/grid-column))
                  (dom/div nil
                    (dom/a #js {:className "button hollow"} (dom/span nil "Cancel"))
                    (dom/a #js {:className "button"
                                :onClick   #(when-not is-loading? (.update-product component))}
                           (if is-loading?
                             (dom/i #js {:className "fa fa-spinner fa-spin"})
                             (dom/span nil "Save"))))))))

(defn products-list [component store]
  (dom/div nil
    (my-dom/div
      (->> (css/grid-row))
      (my-dom/div
        (->> (css/grid-column))
        (dom/a #js {:className "button"
                    :href (route-store (:db/id store) "/products/create")} "Add product")))

    (my-dom/div
      (->> (css/grid-row))
      (my-dom/div
        (->> (css/grid-column))
        (dom/table
          #js {:className "hover"}
          (dom/thead
            nil
            (dom/tr nil
                    (dom/th nil (dom/span nil "Delete"))
                    (dom/th nil "Product Name")
                    (dom/th nil "Price")
                    (dom/th nil "Last Updated")))
          (dom/tbody
            nil
            (map (fn [p]
                   (dom/tr nil
                           (dom/td nil
                                   (dom/input #js {:type "checkbox"}))
                           (dom/td nil
                                   (dom/a #js {:href (route-store (:db/id store) (str "/products/" (:db/id p)))}
                                          (dom/span nil (:store.item/name p))))
                           (dom/td nil
                                   (dom/a #js {:href (route-store (:db/id store) (str "/products/" (:db/id p)))}
                                          (dom/span nil (:store.item/price p))))
                           (dom/td nil
                                   (dom/a #js {:href (route-store (:db/id store) (str "/products/" (:db/id p)))}
                                          (dom/span nil (:store.item/price p))))))
                 (:store/items store))))))))

(defn store-products [component store {:keys [product-id action] :as rp}]
  (if (or (= action "create") (some? product-id))
    (let [product-id (when (some? product-id)
                       (c/parse-long product-id))
          selected-product (some #(when (= (:db/id %) product-id) %) (:store/items store))]
      (product-edit-form component selected-product))
    (products-list component store)))

(defui StoreDashboard
  static om/IQuery
  (query [_]
    [{:proxy/navbar (om/get-query nav/Navbar)}
     {:query/store [:store/uuid
                    :store/name
                    {:store/owners [{:store.owner/user [:user/email]}]}
                    :store/stripe
                    {:store/items [:store.item/name]}
                    :store/collections]}
     ;{:query/stripe [:stripe/account :stripe/products]}
     :query/route-params
     :query/messages
     ])
  Object
  (initLocalState [_]
    {:selected-tab :products})

  #?(:cljs
     (update-product [this]
                     (let [{:keys [query/route-params]} (om/props this)
                           {:keys [input-price input-name]} form-elements
                           title (.-value (get-element input-name))
                           price (.-value (get-element input-price))]

                       (cond (some? (:product-id route-params))
                             (om/transact! this `[(stripe/update-product ~{:product    {:name     title
                                                                                        :price    price
                                                                                        :currency "CAD"}
                                                                           :product-id (:product-id route-params)
                                                                           :store-id   (:store-id route-params)})
                                                  :query/store])

                             (= (:action route-params) "create")
                             (om/transact! this `[(stripe/create-product ~{:product  {:name     title
                                                                                      :price    price
                                                                                      :currency "CAD"}
                                                                           :store-id (:store-id route-params)})
                                                  :query/store])))))

  (render [this]
    (let [{:keys [proxy/navbar query/store query/route-params]} (om/props this)
          {:keys [dashboard-option]} route-params]
      (debug "My Store: " store)
      (debug "Route params: " route-params)
      ;(debug "PRoducts: " stripe)
      (dom/div #js {:id "sulo-my-store" :className "sulo-page"}
        (common/page-container
          {:navbar navbar}
          (my-dom/div
            (->> (css/grid-row))

            (my-dom/div
              (->> (css/grid-column))
              (menu/horizontal
                (css/add-class :store-nav)
                ;(menu-item "/products" "Products" (= dashboard-option "products"))
                ;(menu-item "/orders" "Orders" (= dashboard-option "orders"))
                (menu/item-tab {:active? (= dashboard-option "products")
                                 :href (route-store (:db/id store) "/products")} "Products")
                (menu/item-tab {:active? (= dashboard-option "orders")
                                 :href (route-store (:db/id store) "/orders")} "Orders"))))
          (cond (= dashboard-option "products")
                (store-products this store route-params)
                (= dashboard-option "orders")
                (store-orders this store route-params)))))))

(def ->StoreDashboard (om/factory StoreDashboard))
