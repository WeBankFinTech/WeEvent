#!/bin/bash
current_path=$(pwd)

# check processor
function check_processor(){
    echo "check processor service "

    if [[ ! -e ${current_path}/conf/application-prod.properties ]];then
        echo "${current_path}/conf/application-prod.properties not exist"
        exit 1
    fi

    port=$(grep "port" ${current_path}/conf/application-prod.properties| head -1 | awk -F':' '{print $NF}' | sed s/[[:space:]]//g)
    if [[ $? -ne 0 ]];then
        echo "get processor port fail"
        exit 1
    fi

    result=${curl -H "Content-type: application/json" -X POST -d '{"id":"1","ruleName":"airCondition","status":"0"}' http://localhost:7008/processor/insert
    if [[ "${result.errorCode}" == "0" || "${result.errorCode}" == "1" ]];then
        yellow_echo "processor service is ok"
    else
        yellow_echo "processor service is error"
    fi
 }

check_processor

