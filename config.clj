{:port 3001
 :chp-dir "public/chp"
 :public-dir "public"
 :bidi-routes ["/" [[#".*\.chp"  stigmergy.chp/hiccup-page-handler]]]}
