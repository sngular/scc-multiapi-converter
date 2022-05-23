package com.corunet.multiapi.converter;

import java.io.File;
import java.util.Collection;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.ContractConverter;

@Slf4j
public class MultiApiContractConverter implements ContractConverter<Collection<Contract>> {

  @Override
  public boolean isAccepted(final File file) {
    return false;
  }

  @Override
  public Collection<Contract> convertFrom(final File file) {
    return null;
  }

  @Override
  public Collection<Contract> convertTo(final Collection<Contract> contract) {
    return null;
  }
}
