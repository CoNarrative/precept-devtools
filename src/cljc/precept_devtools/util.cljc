(ns precept-devtools.util)

(defn select-kvs-and-rename-keys
  "Returns vector of maps of keys in coll and their values from xs
  with new key names"
  [keys new-keys xs]
  (if (map? xs)
    (filter (comp some? val)
      (zipmap new-keys (vals (select-keys xs keys))))
    (mapv (partial zipmap new-keys)
      (map (fn [m] (vals (select-keys m keys))) xs))))
