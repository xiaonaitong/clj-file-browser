(ns clj-file-browser.repl
  (:use (seesaw core tree ))
  (:require [seesaw.bind :as b])
  (:use clojure.java.io)
  (:use seesaw.mig)
  (:import javax.swing.SwingConstants
           java.io.File))

(def browser (frame :title "File browser" :on-close :dispose))

(config! browser :size [600 :by 600])

(config! browser :content (left-right-split (tree :id :dirs)
                                            (scrollable (mig-panel :id :files
                                                                   :constraints ["wrap 4" "" ""])
                                                        :hscroll :never)
                                                  :one-touch-expandable? true))

(comment (config! browser :content (left-right-split (tree :id :dirs)
                                             (listbox :id :files
                                                      :model (range 10))
                                             :one-touch-expandable? true)))

(def root (java.io.File. "."))

(config! (select browser [:#dirs]) :model (simple-tree-model #(.isDirectory %) #(->> % .listFiles (filter (memfn isDirectory) )) (java.io.File. ".")))

(config!
 (select browser [:#dirs]) :renderer
 (proxy [javax.swing.tree.DefaultTreeCellRenderer] []
   (getTreeCellRendererComponent [t value s e l r h]
     (do
       (proxy-super getTreeCellRendererComponent t value s e l r h)
       (proxy-super setOpenIcon (icon (file "icons/open.png")))
       (proxy-super setClosedIcon (icon (file "icons/closed.png")))
       (proxy-super setText (.getName value)))
     this)))

(require '[seesaw.bind :as b])

(comment (b/bind
  (b/selection (select browser [:#dirs]))
  (b/transform (fn [files]
                 (when (seq files)
                   (let [current (last files)]
                     (when (.isDirectory current)
                       (vec (map #(.getName %) (.listFiles current))))))))
  (b/property (select browser [:#files]) :model)))


(comment (b/bind
  (b/selection (select browser [:#dirs]))
  (b/transform (fn [files]
                 (when (seq files)
                   (let [current (last files)]
                     (when (.isDirectory current)
                       (vec (.listFiles current)))))))
  (b/b-do [files]
          (let [files-pane (select browser [:#files])]
            (.removeAll files-pane)
            (doseq [f files]
              (let [file-icon (-> (if (.isDirectory f)
                                    "icons/folder.png"
                                    "icons/document.png")
                                  file
                                  icon)
                    file-name (apply str (take 20 (.getName f)))]
                (.add files-pane (label :text file-name
                                        :icon  file-icon
                                        :v-text-position :bottom
                                        :h-text-position  :center))))
            (doto files-pane
              (.revalidate )
              (.repaint))))))

;;; refactor out refresh files pane
(defn refresh-files-view [files]
  (let [files-pane (select browser [:#files])]
    (.removeAll files-pane)
    (doseq [f files]
      (let [file-icon (-> (if (.isDirectory f)
                            "icons/folder.png"
                            "icons/document.png")
                          file icon)
            file-name (apply str (take 20 (.getName f)))]
        (.add
         files-pane
         (label :text file-name
                :icon  file-icon
                :v-text-position :bottom
                :h-text-position  :center
                :listen [:mouse-clicked
                         (fn [e]
                           (when (and (= (.getClickCount e) 2)
                                    (not (.isConsumed e))
                                    (.isDirectory f))
                             (refresh-files-view (.listFiles f))
                             (b/selection! (select browser [:#dirs])
                                           (path-to f root))))]))))
    (doto files-pane
      (.revalidate )
      (.repaint))))

(defn path-to [from to]
  (let [path (.getAbsolutePath from)
        topath (.getAbsolutePath to)
        separator (File/separator)]
    (map file (loop [p path r []]
                (if (= p topath)
                  r
                  (let [pos (.lastIndexOf p separator)
                        parent (.substring p 0 pos)]
                    (recur parent (conj r parent))))))))

(def b1 (b/bind
         (b/selection (select browser [:#dirs]))
         (b/transform (fn [files]
                        (when (seq files)
                          (let [current (last files)]
                            (when (.isDirectory current)
                              (vec (.listFiles current)))))))
         (b/b-do* refresh-files-view)))

(def b2 (b/bind
         (b/selection (select browser [:#dirs]))
         (b/transform (fn [files]
                        (when (seq files)
                          (let [current (last files)]
                            (when (.isDirectory current)
                              (vec (map #(.getName %) (.listFiles current))))))))
         (b/property (select browser [:#files]) :model)))
