(ns eponai.server.external.firebase
  (:require
    [clj-http.client :as http]
    [taoensso.timbre :refer [debug warn]]
    [cheshire.core :as cheshire]
    [clojure.java.io :as io]
    [eponai.common :as c]
    [eponai.common.format.date :as date]
    [buddy.core.codecs.base64 :as b64]
    [com.stuartsierra.component :as component])
  (:import (com.google.firebase FirebaseOptions$Builder FirebaseApp)
           (com.google.firebase.auth FirebaseAuth FirebaseCredentials)
           (com.google.firebase.database FirebaseDatabase DatabaseReference ValueEventListener DataSnapshot DatabaseError)
           (com.google.firebase.tasks OnSuccessListener)))

(defprotocol IFirebaseNotifications
  (-send [this user-id data])
  (-register-device-token [this user-id token])
  (-get-device-token [this user-id]))

(defprotocol IFirebaseAuth
  (-generate-client-auth-token [this user-id claims]))

(defprotocol IFirebaseChat
  (-user-online [this store-id]))

(defn ref->snapshot
  "Takes a ref and returns the DataSnapshot for the ref.

  Throws exception if the call times out or is cancelled."
  [^DatabaseReference ref]
  (let [p (promise)]
    (-> ref
        (.addListenerForSingleValueEvent
          (reify ValueEventListener
            (^void onDataChange [this ^DataSnapshot snapshot]
              (deliver p {:snapshot snapshot}))
            (^void onCancelled [this ^DatabaseError error]
              (debug "FIREBASE - Error retrieving data: " error)
              (deliver p {:error error})))))
    (let [{:keys [snapshot error] :as v} (deref p 1000 ::timedout)]
      (cond (= v ::timedout)
            (throw (ex-info (str "Timed out getting value for database reference: " (.getPath ref))
                            {:ref      ref
                             :ref-path (.getPath ref)}))
            (some? error)
            (throw (ex-info (str "Exception when getting value for firebase reference: " (.getPath ref))
                            {:ref      ref
                             :ref-path (.getPath ref)}
                            (.toException error)))
            :else
            snapshot))))

(defn ref->value [^DatabaseReference ref]
  "Returns the value snapshot for a db ref"
  (.getValue (ref->snapshot ref)))

(defn- create-firebase-db [service-account database-url]
  ;; Initialize app once.
  (let [firebase-app (if (not-empty (FirebaseApp/getApps))
                       (FirebaseApp/getInstance)
                       (with-open [service-account (io/input-stream (b64/decode service-account))]
                         (let [opts (-> (FirebaseOptions$Builder.)
                                        (.setCredential (FirebaseCredentials/fromCertificate service-account))
                                        (.setDatabaseUrl database-url)
                                        (.build))]
                           (FirebaseApp/initializeApp opts))))]
    ;; Once the app has been initialized, get the db instance.
    (FirebaseDatabase/getInstance ^FirebaseApp firebase-app)))

(defrecord Firebase [server-key private-key private-key-id service-account database-url]
  component/Lifecycle
  (start [this]
    (if (:database this)
      this
      (let [db (create-firebase-db service-account database-url)]
        (assoc this :database db
                    :refs {:notifications (.getReference db "v1/notifications")
                           :tokens        (.getReference db "v1/tokens")
                           :presence      (doto (.getReference db "v1/presence")
                                            (.keepSynced true))}))))
  (stop [this]
    (dissoc this :database :refs))

  IFirebaseChat
  (-user-online [this user-id]
    (debug "Check online status: " user-id)
    (ref->value (-> (:presence (:refs this))
                    (.child (str user-id)))))

  IFirebaseAuth
  (-generate-client-auth-token [this user-id claims]
    (let [fb-instance (FirebaseAuth/getInstance)
          p (promise)]
      (-> (.createCustomToken fb-instance (str user-id) (clojure.walk/stringify-keys claims))
          (.addOnSuccessListener
            (reify OnSuccessListener
              (^void onSuccess [this customtoken]
                (debug "FIREBASE GOT TOKEN: " customtoken)
                (deliver p customtoken)))))
      (deref p 2000 :firebase/token-timeout)))

  IFirebaseNotifications
  (-send [this user-id {:keys [title message subtitle] :as params}]
    (let [new-notification {:timestamp (date/current-millis)
                            :type      "chat"
                            :title     (c/substring title 0 100)
                            :subtitle  (c/substring subtitle 0 100)
                            :message   (c/substring message 0 100)}
          user-notifications-ref (-> (:notifications (:refs this))
                                     (.child (str user-id "/unread/chat")))]
      (-> (.push user-notifications-ref)
          (.setValue (clojure.walk/stringify-keys new-notification)))

      ;; TODO enable when we are ready to send web push notifications
      (comment
        (let [token (-get-device-token this user-id)]
          (debug "FIREBASE - send chat notification: " new-notification)
          (http/post "https://fcm.googleapis.com/fcm/send"
                     {:form-params {:to   token
                                    :data new-notification}
                      :headers     {"Authorization" (str "key=" server-key)}})))))

  (-register-device-token [this user-id token]
    (when user-id
      (-> (:tokens (:refs this))
          (.child (str user-id))
          (.setValue token))))

  (-get-device-token [this user-id]
    (-> (:tokens (:refs this))
        (.child (str user-id))
        (ref->value))))

(defn firebase-stub []
  (reify
    IFirebaseChat
    (-user-online [this store-id])
    IFirebaseAuth
    (-generate-client-auth-token [this user-id claims]
      "some-token")
    IFirebaseNotifications
    (-send [this user-id data])
    (-register-device-token [this user-id token])
    (-get-device-token [this user-id]
      "some token")))

(defn firebase [{:keys [server-key private-key private-key-id service-account database-url]}]
  (if (some? service-account)
    (map->Firebase {:service-account service-account
                    :server-key      server-key
                    :private-key     private-key
                    :private-key-id  private-key-id
                    :database-url    database-url})
    (do (warn "Got nil for service account. Using firebase-stub.")
        (firebase-stub))))