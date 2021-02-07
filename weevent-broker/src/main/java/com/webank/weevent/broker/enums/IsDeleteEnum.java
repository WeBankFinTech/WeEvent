package com.webank.weevent.broker.enums;

import lombok.Getter;

@Getter
public enum IsDeleteEnum {

    NOT_DELETED(0L, "not deleted");

    private Long code;
    private String value;

    IsDeleteEnum(Long code, String value) {
        this.code = code;
        this.value = value;
    }


}