(load-file "src/clj/lambdacart/core.clj")

{:environment :production
 :port 3001
 :chp-dir "public"
 :public-dir "public"
 :index "mdb-template.chp"
 :mime-types {"chp" "text/html" 
              "blog" "text/html"
              nil "text/html"}
 :bidi-routes ["/" [
                    ["" (fn [req]
                          (lambdacart.core/default-handler {}))]
                    #_[#".*\.chp"  stigmergy.chp/hiccup-page-handler]]]}

