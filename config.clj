{:port 3000
 :chp-dir "public/chp"
 :public-dir "public"
 :bidi-routes ["/" [[#".*\.chp"  stigmergy.chp/hiccup-page-handler]]]}
