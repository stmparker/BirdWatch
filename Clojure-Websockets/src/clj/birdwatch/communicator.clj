(ns birdwatch.communicator
  (:gen-class)
  (:use [birdwatch.conf])
  (:require
   [birdwatch.twitterclient :as tc]
   [birdwatch.channels :as c]
   [birdwatch.atoms :as a]
   [birdwatch.persistence :as p]
   [clojure.edn :as edn]
   [clojure.data.json :as json]
   [clojure.core.match :as match :refer (match)]
   [http.async.client :as ac]
   [clj-time.core :as t]
   [clojure.pprint :as pp]
   [clojure.tools.logging :as log]
   [org.httpkit.server :as http-kit-server]
   [ring.middleware.defaults]
   [ring.util.response :refer [resource-response response content-type]]
   [compojure.core     :as comp :refer (defroutes GET POST)]
   [compojure.route    :as route]
   [taoensso.sente     :as sente]
   [clojure.core.async :as async :refer [<! <!! >! >!! chan put! alts! timeout go]]))

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]}
      (sente/make-channel-socket! {:user-id-fn (fn [req]
                                                 (let [uid (str (java.util.UUID/randomUUID))]
                                                   (log/info "Connected:" (:remote-addr req) uid)
                                                   uid))})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv)         ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn)         ; ChannelSocket's send API fn
  (def connected-uids                connected-uids)) ; Watchable, read-only atom

(defn- event-msg-handler
  [{:as ev-msg :keys [event ?reply-fn]}]
  (match event
         [:cmd/percolate params] (p/start-percolator params)
         [:cmd/query params]     (do
                                   (log/info "Received query:" params)
                                   (let [res (p/query params)] ; TODO: put request msg on channel for async handling
                                     (chsk-send! (:uid params) [:tweet/prev-chunk res])))
         [:cmd/missing params]   (put! c/tweet-missing-chan params)
         [:chsk/ws-ping params]  () ; currently just do nothing with ping (no logging either)
         :else                   (do (log/info "Unmatched event:" event)
                                   (when-not (:dummy-reply-fn? (meta ?reply-fn))
                                     (?reply-fn {:umatched-event-as-echoed-from-from-server event})))))

(defonce chsk-router (sente/start-chsk-router! ch-chsk event-msg-handler))

;; loop for matching connected clients with percolation matches and delivering those on the appropriate socket
(go
 (while true
   (let [[t matches] (<! c/percolation-matches-chan)]
     (doseq [uid (:any @connected-uids)]
       (when (contains? matches (get @a/subscriptions uid))
         (chsk-send! uid [:tweet/new t]))))))

;; loop sending stats about number of connected users to all connected clients
(go
 (while true
   (<! (timeout 2000))
   (let [uids (:any @connected-uids)]
     (doseq [uid uids]
       (chsk-send! uid [:stats/users-count (count uids)])))))

;; loop sending stats about number of indexed tweets to all connected clients
(go
 (while true
   (<! (timeout 3000))
   (let [uids (:any @connected-uids)
         total-tweet-count (format "%,15d" (:count (p/total-tweet-count)))]
     (doseq [uid uids]
       (chsk-send! uid [:stats/total-tweet-count total-tweet-count])))))


;; loop for sending missing tweet back to client
(go
 (while true
   (let [msg (<! c/missing-tweet-found-chan)]
     (chsk-send! (:uid msg) [:tweet/missing-tweet (:tweet msg)]))))
