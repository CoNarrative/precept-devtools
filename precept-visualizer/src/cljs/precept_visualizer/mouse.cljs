(ns precept-visualizer.mouse
  (:require [precept.core :as precept]))


(defonce handlers-store (atom {}))


(defn namespaced-event-kw [event-kw]
  (keyword "precept-visualizer.mouse" (name event-kw)))


;; Preserving in case we want to provide premade transform fns via options map
(defn get-event-transform [options]
  (cond
    (:event-transform options) (:event-transform options)
    :else nil))


(defn default-mouse-fact [a v] [:transient a v])


(defn default-mouse-handler [a e]
  (precept/then (default-mouse-fact a e)))


(def default-mouse-event-attributes [::mouse-down ::mouse-up ::mouse-move ::click])


(defn get-default-handlers
  "Returns default handlers for default mouse events.
  If a transformation was specified in options, applies it to each event object
  before calling event handler."
  [options]
  (if-let [event-transform (get-event-transform options)]
    (reduce
      (fn [m a]
        (assoc m a (fn [e] (default-mouse-handler a (event-transform e)))))
      {}
      default-mouse-event-attributes)
    (reduce
      (fn [m a]
        (assoc m a (fn [e] (default-mouse-handler a e))))
      {}
      default-mouse-event-attributes)))

(defn remove-listener! [ns-event-kw handler]
  (.removeEventListener js/window
    (clojure.string/replace (name ns-event-kw) "-" "")
    handler))


(defn remove-listeners! [keys]
  (let [ns-event-kws (mapv namespaced-event-kw keys)
        handlers (select-keys @handlers-store ns-event-kws)]
    (doseq [[ns-event-kw handler] handlers]
      (remove-listener! ns-event-kw handler))))


(defn remove-all-listeners! []
  (remove-listeners! (mapv name default-mouse-event-attributes)))


;; should be "upsert" / create or replace listener
(defn add-listener!
  "Removes listener for event if exists in handlers-store. Adds provided listener."
  [handlers-store handler ns-event-kw]
  (do
    (when-let [existing-handler (ns-event-kw @handlers-store)]
      (remove-listener! ns-event-kw existing-handler))
    (.addEventListener js/window
      (clojure.string/replace (name ns-event-kw) "-" "")
      handler)
    (swap! handlers-store
      assoc ns-event-kw
      handler)))

;; TODO. change argument order so handlers only can be provided... options should be optional, not required
(defn add-listeners!
  "Adds mouse event listeners to window. When no `event-handlers` map is
  provided, inserts the mouse event object as a `:transient` fact for each
  attribute in `default-mouse-event-attributes`. Otherwise registers provided
  handlers for their corresponding events.
  `options`:
    - `event-fn` - Function called on every event. Useful to e.g. call `.persist` on each event object
      when required required by Reagent or other React frameworks.
  "
  ([{:keys [event-fn] :as options}]
   (let [handlers (get-default-handlers (or options {}))]
     (doseq [[ns-event-kw handler] handlers]
       (add-listener! handlers-store handler ns-event-kw))))
  ([{:keys [mouse-down mouse-up mouse-move click] :as event-handlers}
    {:keys [event-fn] :as options}]
   (let [handlers (if (empty? event-handlers)
                    (get-default-handlers options)
                    event-handlers)]
     (doseq [[event-kw handler] handlers]
       (let [ns-event-kw (namespaced-event-kw event-kw)]
         (add-listener! handlers-store handler ns-event-kw))))))

