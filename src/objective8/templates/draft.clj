(ns objective8.templates.draft
  (:require [net.cgrand.enlive-html :as html]
            [net.cgrand.jsoup :as jsoup]
            [objective8.templates.page-furniture :as f]
            [objective8.utils :as utils]))

(def library-html "templates/jade/library.html")
(def draft-list-template (html/html-resource "templates/jade/draft-list.html" {:parser jsoup/parser}))

(html/defsnippet no-drafts
  library-html [:.clj-no-drafts-yet] [translations]
  [:.clj-no-drafts-yet-text] (html/content (translations :draft-list/no-drafts)))

(defn- local-draft-path
  ([draft] (local-draft-path draft false))
  ([draft latest] (if draft (utils/local-path-for :fe/draft :id (:objective-id draft) :d-id
                                       (if latest "latest" (:_id draft))))))

(defn latest-draft? [drafts translations]
  (let [draft (first drafts)]
    (html/transformation [:.clj-latest-draft-wrapper] (if (empty? drafts)
                                                        (html/substitute (no-drafts translations))
                                                        identity)
                         [:.clj-latest-draft-title] (html/content (translations :draft-list/latest-draft))
                         [:.clj-latest-draft-link] (html/set-attr "href" (local-draft-path draft true))
                         [:.clj-latest-draft-writer] (html/content (:username draft))
                         [:.clj-latest-draft-time] (html/content (utils/iso-time-string->pretty-time (:_created_at draft))))))

(defn draft-list-page [{:keys [translations data user] :as context}]
  (let [drafts (:drafts data)
        objective (:objective data)]
    (apply str
           (html/emit*
             (html/at draft-list-template
                      [:title] (html/content (get-in context [:doc :title]))
                      [:.clj-masthead-signed-out] (html/substitute (f/masthead context))
                      [:.clj-status-bar] (html/substitute (f/status-flash-bar context))
                      [:.clj-objective-progress-indicator] nil
                      [:.clj-guidance-buttons] nil

                      [:.clj-guidance-heading] (html/content (translations :draft-guidance/heading))
                      [:.clj-guidance-text-line-1] (html/content (translations :draft-guidance/text-line-1))
                      [:.clj-guidance-text-line-2] (html/content (translations :draft-guidance/text-line-2))

                      [:.clj-draft-list-title] (html/content (str (translations :draft-list/drafts-for) ": "
                                                                  (:title objective)))

                      [:.clj-latest-draft-wrapper] (latest-draft? drafts translations)

                      [:.clj-add-a-draft] (when (utils/writer-for? user (:_id objective))
                                            (html/do->
                                              (html/set-attr :href
                                                             (utils/local-path-for :fe/add-draft-get
                                                                                   :id (:_id objective)))
                                              (html/content (translations :draft-list/add-a-draft))))

                      [:.clj-previous-drafts-title] (html/content (translations :draft-list/previous-versions))

                      [:.clj-previous-drafts-list] (if (empty? (rest drafts))
                                                     (html/substitute (translations :draft-list/no-previous-versions)) 
                                                     identity)

                      [:.clj-previous-draft-item] (html/clone-for [draft (rest drafts)] 
                                                                  [:.clj-previous-draft-link] (html/set-attr "href" (local-draft-path draft))
                                                                  [:.clj-previous-draft-writer] (html/content (:username draft))
                                                                  [:.clj-previous-draft-time] (html/content (utils/iso-time-string->pretty-time (:_created_at draft)))) 
                      

                      )))))
