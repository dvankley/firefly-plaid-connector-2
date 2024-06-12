#!/bin/bash

############
<<GuideDoc

- WARNING: ADDS A LOT OF RULES 
- WARNING IDEAL PERHAPS ONLY FOR NEW INSTALLATIONS 
- USE AT YOUR OWN RISK

This script facilitates a part of the integration of the firefly/plaid connector.
It will download the csv file from plaid.com at the start
It auto generates categories and budgets to match the
detailed and primary categories of the plaid category csv file.

1. Set your Token and Endpoint
2. Create A rule Group in firefly called: Plaid Tag to Category
3. chmod 755 this script
4. run the script
5. all new transactions from plaid should hit this rule and get put in a category and budget

On my pi this script takes like 15min to run.


GuideDoc
#########

#Set these two correctly
TOKEN="YOURTOKENGOESHERE"
ENDPOINT="http://localhost:9090/api/v1"


#Get the CSV file from plaid
curl -O  https://plaid.com/documents/transactions-personal-finance-category-taxonomy.csv

### This Adds All the Rule Mapping

i=0

while read item; do

budget=$(echo $item | tr '[:upper:]' '[:lower:]' |  cut -f  1 -d , | sed s/_/-/g)
detcat=$(echo $item |  cut -f  2 -d , | tr '[:upper:]' '[:lower:]' | sed s/_/-/g | sed s/${budget}-//g)

curl -X POST \
-H "Authorization: Bearer ${TOKEN}" \
-H "Content-Type: application/json" \
-H 'accept: application/vnd.api+json' \
-d "{ \"name\": \"${budget}\", \"active\": true }" \
${ENDPOINT}/budgets

curl -X POST \
-H "Authorization: Bearer ${TOKEN}" \
-H "Content-Type: application/json" \
-H 'accept: application/vnd.api+json' \
-d "{ \"name\": \"${detcat}\", \"active\": true }" \
${ENDPOINT}/categories

curl -X POST \
-H "Authorization: Bearer ${TOKEN}" \
-H "Content-Type: application/json" \
-H 'accept: application/vnd.api+json' \
-d "{ \"title\": \"Plaid ${detcat} Mapping\", \"rule_group_title\": \"Plaid Tag to Category\", \"order\": ${i},\"trigger\": \"store-journal\", \"active\": true, \"strict\": true, \"stop_processing\": false, \
\"triggers\": [ \
{\"type\": \"has_no_category\", \"value\": \"true\", \"order\": 1, \"active\": true, \"stop_processing\": false}, \
{\"type\": \"tag_starts\", \"value\": \"plaid-detailed-cat-${detcat}\", \"order\": 2, \"active\": true, \"stop_processing\": false}
], \
 \"actions\": [ \
{\"type\": \"set_category\", \"value\": \"${detcat}\", \"order\": 1, \"active\": true, \"stop_processing\": false}, \
{\"type\": \"set_budget\", \"value\": \"${budget}\", \"order\": 2, \"active\": true, \"stop_processing\": false} \
] \
}" \
${ENDPOINT}/rules


i=$((i+1))

done < transactions-personal-finance-category-taxonomy.csv
