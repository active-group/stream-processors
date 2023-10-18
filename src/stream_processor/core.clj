(ns stream-processor.core)

(defrecord Put [value next])
(defrecord Get [consume])
(defrecord Stop [])
(def stop (Stop.))

(defn >>>
  [sp1 sp2]
  (cond
   ;; put >>> get
   (and (instance? Put sp1) (instance? Get sp2))
   (>>> ((:next sp1)) ((:consume sp2) (:value sp1)))
   ;; sp >>> put
   (instance? Put sp2)
   (Put. (:value sp2) (fn [] (>>> sp1 ((:next sp2)))))
   ;; get >>> get
   (and (instance? Get sp1) (instance? Get sp2))
   (Get. (fn [i] (>>> ((:consume sp1) i) sp2)))
   ;; stop >>> sp2
   (instance? Stop sp1) sp2
   (instance? Stop sp2) stop))

(defn run
  [sp]
  (cond
   (instance? Put sp) (lazy-seq (cons (:value sp) (run ((:next sp)))))
   (instance? Get sp) '()
   (instance? Stop sp) '()))

(def sp1 (Put. 5 (fn [] (Put. 3 (fn [] stop)))))
(def sp2 (Get. (fn [x] (Get. (fn [y] (Put. (+ x y) (fn [] stop)))))))

(defmacro stream-processor
  [& ?clauses]
  (if (seq? ?clauses)
    (let [?clause (first ?clauses)
          ?rest (rest ?clauses)]
      (case (first ?clause)
        get 
        (let [?var (second ?clause)]
          `(Get. (fn [~?var]
                   (stream-processor ~@?rest))))
        
        put
        (let [?val (second ?clause)]
          `(Put. ~?val (fn [] (stream-processor ~@?rest))))
        
        when
        (let [?test (second ?clause)
              ?consequent (rest (rest ?clause))]
          `(if ~?test
             (stream-processor ~@?consequent)
             (stream-processor ~@?rest)))
        
        ?clause))
    `stop))

(comment (defn sp-from [n] (Put. n (fn [] (sp-from (+ n 1))))))

(defn sp-from [n] (stream-processor (put n) (sp-from (+ n 1))))

(comment
  (defn sp-filter
    [p]
    (Get. 
     (fn [x]
       (if (p x)
         (Put. x
               (fn []
                 (sp-filter p)))
         (sp-filter p))))))

(defn sp-filter
  [p]
  (stream-processor
   (get x)
   (when (p x)
     (put x)
     (sp-filter p))
   (sp-filter p)))

(def nats (sp-from 0))

(def sp-evens (sp-filter even?))

