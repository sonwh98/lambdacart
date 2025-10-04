{:port 3001
 :chp-dir "public/chp"
 :public-dir "public"
 :image-upload-dir "public/images"
 :bidi-routes ["/" [["" (fn [req] (stigmergy.chp/hiccup-page-handler (assoc req :uri "/main.chp")))]
                    [#".*\.chp"  stigmergy.chp/hiccup-page-handler]
                    ["upload" lambdacart.server/upload-handler]]]}
