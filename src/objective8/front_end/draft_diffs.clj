(ns objective8.front-end.draft-diffs
  (:require [diff-match-patch-clj.core :as dmp]
            [clojure.tools.logging :as log]
            [objective8.utils :as utils]))

(declare replacement-element-and-updated-diffs)

(defn replacement-diffs-for-n-chars 
  ([n diffs] 
   (replacement-diffs-for-n-chars n diffs []))
  ([n diffs replacement]
   (if (and (> n 0) (empty? diffs))
     (do
       (log/error "Error when finding diffs for n chars. Should never see this.")
       {:replacement replacement
        :updated-diffs diffs}) 
     (let [diff-element (first diffs)
           diff-element-string (last diff-element)
           diff-element-char-count (count diff-element-string)]
       (if (<= n diff-element-char-count)
         (let [extracted-diff-element (conj (pop diff-element) (subs diff-element-string 0 n)) 
               remaining-diff-element-string (subs diff-element-string n)
               updated-diff-element (when-not (empty? remaining-diff-element-string) 
                                      (conj (pop diff-element) remaining-diff-element-string))]
           {:replacement (conj replacement extracted-diff-element) 
            :updated-diffs (remove nil? (cons updated-diff-element (rest diffs)))})
         (recur (- n diff-element-char-count) (rest diffs) (conj replacement diff-element)))))))

(defn replacement-content-and-updated-diffs [content diffs]
  ;; Each thing in content will be a string or a vector (another html element)
  (if (empty? content)
    {:replacement-content content
     :updated-diffs diffs} 
    (let [first-element (first content)]
      (if (string? first-element)
        (let [{:keys [replacement updated-diffs]} (replacement-diffs-for-n-chars (count first-element) diffs)
              {recursive-replacement :replacement-content 
               recursive-updated-diffs :updated-diffs} (replacement-content-and-updated-diffs (rest content) updated-diffs)]
          {:replacement-content (into [] (concat replacement recursive-replacement)) 
           :updated-diffs recursive-updated-diffs})

        ;; else first-element must be a nested-tag vector
        (let [{:keys [replacement-element updated-diffs]} (replacement-element-and-updated-diffs first-element diffs)
              {recursive-replacement :replacement-content 
               recursive-updated-diffs :updated-diffs} (replacement-content-and-updated-diffs (rest content) updated-diffs)] 
          {:replacement-content (into [] (concat [replacement-element] recursive-replacement))
           :updated-diffs recursive-updated-diffs})))))

(defn replacement-element-and-updated-diffs [element diffs] 
  (let [{:keys [element-without-content content]} (utils/split-hiccup-element element) 
        {:keys [replacement-content updated-diffs]} (replacement-content-and-updated-diffs content diffs)]
    {:replacement-element (into [] (concat element-without-content replacement-content)) 
     :updated-diffs updated-diffs})) 

(defn insert-diffs-into-draft 
  ([diffs draft-without-diffs] 
   (insert-diffs-into-draft  diffs draft-without-diffs nil))
  ([diffs draft-without-diffs draft-with-diffs] 
   (if (empty? draft-without-diffs)
     draft-with-diffs
     (let [element (first draft-without-diffs)
           {:keys [replacement-element updated-diffs]} (replacement-element-and-updated-diffs 
                                                         element diffs)
           updated-draft-with-diffs (concat draft-with-diffs (list replacement-element))]
       (recur updated-diffs (rest draft-without-diffs) updated-draft-with-diffs)))))

(defn remove-hiccup-elements [hiccup element]
  (remove #(= element (first %)) hiccup))

(defn strings-for-element [element]
  ;; Element is either a string or a vector (a hiccup html element)
  (if (string? element)
    [element]
    (->> (utils/split-hiccup-element element)
         :content
         (mapcat strings-for-element))))

(defn hiccup->content-string [draft]
  (clojure.string/join (mapcat strings-for-element draft))) 

(defn diff-hiccup-content [hiccup-1 hiccup-2]
  (-> (dmp/diff (hiccup->content-string hiccup-1) (hiccup->content-string hiccup-2))
      dmp/cleanup!
      dmp/as-hiccup))

(defn get-diffs-between-drafts [draft previous-draft]
  (let [current-draft-content (utils/sanitise-hiccup (:content draft))
        previous-draft-content (utils/sanitise-hiccup (:content previous-draft))
        diffs (diff-hiccup-content previous-draft-content current-draft-content)
        previous-draft-diffs (remove-hiccup-elements diffs :ins)
        current-draft-diffs (remove-hiccup-elements diffs :del)]
    {:previous-draft-diffs (insert-diffs-into-draft previous-draft-diffs previous-draft-content)
     :current-draft-diffs (insert-diffs-into-draft current-draft-diffs current-draft-content)}))
