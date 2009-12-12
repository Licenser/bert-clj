(ns com.trottercashion.bert-clj.etf-decoder
  (:use com.trottercashion.bert-clj.utility))

(defn unsign-int [i]
  (if (< i 0)
    (+ 256 i)
    i))

(defn make-byte-array [coll]
  (let [array (make-array Byte/TYPE (count coll))]
    (doseq [[idx val] (zipmap (iterate inc 0) coll)]
      (aset array idx (byte val)))
    array))

(defn bytes->data [data]
  (reduce #(+ (bit-shift-left %1 8) (unsign-int %2)) 0 data))

(defn bytes->string-with-length [data]
  (let [length (bytes->data (take 2 data))
        bytes  (take length (drop 2 data))]
    [(String. (make-byte-array bytes)) (+ length 2)]))

(defmacro defdecoder [type & body]
  `(defmethod decode-with-length ~type [coll#]
     (let [[_# & data#] coll#
           bodyfun# (fn ~(first body) ~@(rest body))
           [obj# size#] (bodyfun# data#)]
       [obj# (inc size#)])))

(defmulti decode-with-length
  (fn [coll]
    (let [[type data] coll]
      (*codes->etf-types* (int type)))))

(defdecoder :string [data]
  (bytes->string-with-length data))

(defdecoder :float [data]
  [(Float. (String. (make-byte-array (take 26 data)))) 26])

(defdecoder :small-int [data]
  [(bytes->data (take 1 data)) 1])

(defdecoder :big-int [data]
  (let [unsigned (bytes->data (take 4 data))
        biggest-signed 2147483647]
    [(if (> unsigned biggest-signed)
       (- unsigned (bit-shift-left 1 32))
       unsigned)
     4]))

;; should we go to atom or keyword here?
(defdecoder :atom [data]
  (let [ret (bytes->string-with-length data)]
    [(symbol (first ret)) (second ret)]))

(defn read-data [data count]
  (if (< count 1)
    nil
    (let [[obj size] (decode-with-length data)]
      (cons [obj size] (read-data (drop size data) (dec count))))))

(defdecoder :small-tuple [data]
  (let [size            (unsign-int (first data))
        body            (drop 1 data)
        objs-with-sizes (read-data body size)
        objs            (map first objs-with-sizes)
        size            (reduce #(+ %1 (second %2)) 0 objs-with-sizes)]
    [(vec objs) (inc size)]))

(defn decode [coll]
  (let [[magic & data] coll]
    (if (= magic -125)
      (first (decode-with-length data))
      (throw "Unknown magic bit: we only handle 131 (-125 when signed)"))))
