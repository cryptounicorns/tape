(ns app.screens.live
  (:require-macros [app.macros :refer [profile]]
                   [klang.core :refer [info! warn! erro! crit! fata! trac!]])
  (:require [reagent.core :as r]
            [clojure.string :as s]
            [goog.object :as gobj]
            [cljsjs.moment]
            [cljsjs.react-select]
            [cljsjs.react-virtualized]
            [app.db :refer [db]]
            [app.motion :refer [Motion spring presets]]
            [app.components.ui :as ui]
            [app.components.header :refer [header]]
            [app.components.chart :refer [Chart]]
            [app.logic.ui :refer [get-chart-points]]
            [app.actions.ui :refer
             [add-to-favs
              remove-from-favs
              close-detailed-view]]
            [app.logic.curr :refer
             [best-pairs
              all-pairs
              user-favs
              pairs-by-query
              pairs-by-market]]
            [app.actions.ui :refer
             [toggle-filter
              update-filter-q
              open-detailed-view
              toggle-filterbox
              update-filter-market]]))

(defn render-row
  [pair]
  (let [{:keys [market symbol-pair last changes]} pair
        {:keys [percent amount]} changes
        amount (if amount (.toFixed amount 2) nil)
        percent (if percent (.toFixed percent 2) nil)
        swing-class (if percent (if (pos? percent) "up" "down") "")]
    [:div.row_wrap
     [:div.left_cell
      [:div.title symbol-pair]
      [:div.market market]]
     [:div.right_cell
      [:span last]
      [:div.swing {:class swing-class}
       (if (and changes amount percent)
         (str (if (pos? percent) "+" "") percent  "% "
              (if (pos? amount) "+" "") amount)
         "n/a")]]]))

(defn keyword<->str
  [v]
  (if (string? v)
    (-> v
        (s/replace " " "")
        .toLowerCase
        keyword)
    (condp = v
      :bestprice "Best price"
      :favorites "Favorites"
      :market "Market"
      nil (erro! (str "Not a string/keyword " v)))))

(defn render-rows []
  (let [markets @(r/cursor db [:markets])
        favs @(r/cursor db [:user :favorites])
        curr-filter @(r/cursor db [:ui/current-filter])
        q @(r/cursor db [:ui/filter-q])
        market-filter @(r/cursor db [:ui/market-filter])
        pairs (condp = curr-filter
                :favorites @(r/track user-favs markets favs)
                :market @(r/track pairs-by-market markets market-filter)
                nil @(r/track all-pairs markets))
        [dt-m dt-p] @(r/cursor db [:ui/detailed-view])
        filtered @(r/track pairs-by-query pairs q)
        filtered (remove nil? filtered)]
      [:div.rows_wrapper
        [:h1 {:style {:padding "0 10px"}} (str "Total pairs " (count filtered))]
       ; (for [pair filtered])]
        [:> js/ReactVirtualized.AutoSizer
         (fn [_]
          (r/as-element
             [:> js/ReactVirtualized.List
               {:height 480
                :width 320
                :headerHeight 70
                :rowHeight 45
                :rowCount (count filtered)
                :rowRenderer
                  (fn [x]
                   (let [index (aget x "index")]
                     (r/create-element
                      "div"
                      #js{:style (aget x "style")
                          :key (aget x "key")}
                      (r/as-element [render-row (get (vec filtered) index)]))))}]))]]))

         ; (let [{:keys [market symbol-pair]} pair
         ;       [kw-m kw-p] (mapv keyword [market symbol-pair])]
         ;   ^{:key (str pair market)}
         ;   [:div
         ;    {:on-click #(open-detailed-view kw-m kw-p)
         ;     :style {:background-color (if (and (= dt-m kw-m) (= dt-p kw-p))
         ;                                 "rgba(0, 126, 255, 0.04)"
         ;                                 "white")}}
         ;    [render-row pair]]))]))

(defn select-q
  []
  (let [opts ["Favorites" "Market"]
        v @(r/cursor db [:ui/current-filter])
        on-change #(if % (toggle-filter (keyword<->str (aget % "value"))))]
    [:>
     js/window.Select
     {:value (keyword<->str v)
      :onChange on-change
      :options (clj->js (map #(zipmap [:value :label] [% %]) opts))}]))

(defn select-market
  []
  (let [opts @(r/track #(-> @db
                            :markets
                            keys
                            (as-> x (map name x))))
        v @(r/cursor db [:ui/market-filter])
        on-change #(if % (update-filter-market (keyword (aget % "value"))))]
    [:>
     js/window.Select
     {:value v
      :onChange on-change
      :options (clj->js (map #(zipmap [:value :label] [% %]) opts))}]))

;; - Filter
;;
(defn filter-box
  []
  (let [q @(r/cursor db [:ui/filter-q])
        f @(r/cursor db [:ui/current-filter])
        open? @(r/cursor db [:ui/filterbox-open?])]
    [:div#filter_box
     [ui/text-input
      {:on-change #(update-filter-q (-> % .-target .-value))
       :value @(r/cursor db [:ui/filter-q])
       :label "search"}]
     [ui/input-wrap "Filter" [select-q {:key "filter"}]]
     [ui/input-wrap "Market" [select-market {:key "market"}]]]))

(comment {:high 3143.5286
          :sell 3119.8
          :buy 3081.6715
          :vol-cur 98.522881
          :low 3048.4535
          :avg 3095.991
          :market "yobit"
          :timestamp 1509279292
          :symbol-pair "LTC-RUB"
          :last 3070
          :vol 304628.34})

(defn fav?
  [favs tupl]
  (reduce (fn [acc pair]
            (if (and (= (first pair) (first tupl)) (= (last pair) (last tupl)))
              true
              acc))
          false
          favs))

(defn pair-detailed
  []
  (let [[market pair] @(r/cursor db [:ui/detailed-view])
        favs @(r/cursor db [:user :favorites])
        content (get-in @db [:markets market pair])
        {:keys [high
                low
                sell
                buy
                symbol-pair
                market
                timestamp
                avg
                last
                vol
                vol-cur]}
        content
        is-fav? (fav? favs [market pair])
        points @(r/track get-chart-points market pair)]
    (when (:ui/detailed-view @db)
      [:div#detailed
       [:div.header
        [:div.title
         pair
         [:div.fav
          {:class (if is-fav? "faved" "")
           :on-click (if is-fav?
                       #(remove-from-favs [(keyword market) (keyword pair)])
                       #(add-to-favs [(keyword market) (keyword pair)]))}
          (if is-fav? "saved" "save")]]
        [:div.close {:on-click #(close-detailed-view)}]]
       [:div.market " " market]
       [:div.labels
        (for [i ["High" "Low" "Buy" "Sell"]] ^{:key i} [:div.item i])]
       [:div.prices.last
        (for [i [high low buy sell]]
          ^{:key (* 1000 (.random js/Math i))} ;; nothing to be proud about here
          [:div.item (js/parseFloat i)])]
       (when points [Chart points])])))

(def height 400)

(def animated-comp
  (r/reactify-component (fn [{c :children}]
                          (let [y (gobj/get c "y")]
                            [:div.detailed_view
                             {:style {:transform (str "translateY(" y "px)")}}
                             [pair-detailed]]))))

(defn detailed-view
  []
  (fn [] [:div.motion_wrapper
          [Motion
           {:style {:y (spring (if (:ui/detailed-view @db) (- height) 0))}}
           (fn [x] (r/create-element animated-comp #js {} x))]]))

(defn live-board
  []
  (fn []
   (let [spin? (-> @db :ui/fetching-init-data?)]
      (if spin?
        [:div.spinner]
        [:div
         [header]
         [filter-box]
         [render-rows]
         [detailed-view]]))))
