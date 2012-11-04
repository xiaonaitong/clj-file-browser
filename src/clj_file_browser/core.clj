(ns clj-file-browser.core
  (:gen-class)
  (:use (seesaw core tree mig))
  (:use [seesaw.selection :only [Selection]])
  (:require [seesaw.bind :as b])
  (:use clojure.java.io)
  (:import javax.swing.SwingConstants
           javax.swing.tree.TreePath
           javax.swing.JTree
           java.io.File
           clj_file_browser.WrapLayout))

(defn wrap-panel
  [& opts]
  (abstract-panel (WrapLayout.) opts))

(def browser (frame :title "File browser" :on-close :dispose))

(config! browser :size [600 :by 600])

(config! browser :content (left-right-split (tree :id :dirs)
                                            (scrollable (wrap-panel :id :files)
                                                        :hscroll :never)
                                                  :one-touch-expandable? true))

(def root (java.io.File. "."))

(config! (select browser [:#dirs]) :model (simple-tree-model #(.isDirectory %) #(->> % .listFiles (filter (memfn isDirectory) )) (java.io.File. ".")))

(config!
 (select browser [:#dirs]) :renderer
 (proxy [javax.swing.tree.DefaultTreeCellRenderer] []
   (getTreeCellRendererComponent [t value s e l r h]
     (do
       (proxy-super getTreeCellRendererComponent t value s e l r h)
       (proxy-super setOpenIcon (icon (resource "icons/open.png")))
       (proxy-super setClosedIcon (icon (resource "icons/closed.png")))
       (proxy-super setText (.getName value)))
     this)))

(declare path-to)
(defn refresh-files-view [files]
  (let [files-pane (select browser [:#files])
        tree-view (select browser [:#dirs])]
    (.removeAll files-pane)
    (doseq [f files]
      (let [file-icon (-> (if (.isDirectory f)
                            "icons/folder.png"
                            "icons/document.png")
                          resource icon)
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
                             (selection! tree-view (path-to f root))))]))))
    (doto files-pane
      (.revalidate )
      (.repaint))))

(extend-protocol Selection
  javax.swing.JTree
    (get-selection [target] (seq (map #(seq (.getPath ^javax.swing.tree.TreePath %)) (.getSelectionPaths target))))
    (set-selection [target [v :as args]]
      (if (seq args)
        (.setSelectionPath target (TreePath. (to-array v)))
        (.clearSelection target))))

(defn path-to [from to]
  (reverse (take-while identity (iterate #(.getParentFile %) from))))

(def b1 (b/bind
         (b/selection (select browser [:#dirs]))
         (b/transform (fn [files]
                        (when (seq files)
                          (let [current (last files)]
                            (when (.isDirectory current)
                              (vec (.listFiles current)))))))
         (b/b-do* refresh-files-view)))

(defn -main [& args]
  (-> browser
      show!))
