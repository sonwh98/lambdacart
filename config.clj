{:port 3001
 :chp-dir "public/chp"
 :public-dir "public"
 :image-upload-dir "public/images"
 :bidi-routes ["/" [[#".*\.chp"  stigmergy.chp/hiccup-page-handler]
                    ["upload" lambdacart.server/upload-handler]]]}
