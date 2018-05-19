#!/usr/bin/env bash


cd precept-visualizer
lein do clean, cljsbuild once min

cd ..

client_resources=precept-visualizer/resources/public
server_resources=resources/public

cp -r ${client_resources}/js/* ${server_resources}/js
cp -r ${client_resources}/css/* ${server_resources}/css
cp -r ${client_resources}/svg/* ${server_resources}/svg
cp ${client_resources}/index.html ${server_resources}/index.html
