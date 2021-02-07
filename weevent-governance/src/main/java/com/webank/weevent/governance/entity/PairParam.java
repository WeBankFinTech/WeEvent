package com.webank.weevent.governance.entity;

import com.webank.weevent.file.IWeEventFileClient;
import com.webank.weevent.file.inner.DiskFiles;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PairParam {

    private IWeEventFileClient weEventFileClient;
    private DiskFiles diskFiles;
    private String nodeAddress;

}
