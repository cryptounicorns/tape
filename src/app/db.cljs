(ns app.db
  (:require [reagent.core :as r]
            [clojure.walk]
            [camel-snake-kebab.core :refer [->kebab-case]]))

(defonce router
  (r/atom
   {:screen :bestprice}))

(defonce db
  (r/atom
    {:ui/bestprice {:sort :asc}
     :ui/expanded-row []

     :settings {:pairs-view :images}

     :portfolio []

     :markets {"bitfinex" {"BTC-USD" {}
                           "LTC-USD" {}}
               "yobit" {"BTC-RUB" {}
                        "BTC-USD" {}
                        "LTC-USD" {}
                        "LTC-RUB" {}}
               "cex" {"BTC-RUB" {}
                      "BTC-USD" {}}}}))

(defn process-ws-event [t]
 (clojure.walk/keywordize-keys
  (into {}
   (for [[k v] t]
        [(->kebab-case k) v]))))

(defn update-ticker! [ticker]
  (let [t (process-ws-event ticker)
        {:keys [market currency-pair]} t]
    (swap! db assoc-in [:markets market currency-pair] t)))

