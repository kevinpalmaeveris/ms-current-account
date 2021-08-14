package com.everis.currentaccount.controller;

import com.everis.currentaccount.dto.message;
import com.everis.currentaccount.model.*; 
import com.everis.currentaccount.service.currentAccountService; 

import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.*;

@RestController
@CrossOrigin(
  origins = "*",
  methods = {
    RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE
  }
)
@RequestMapping
public class currentAccountController {
  @Autowired
  currentAccountService service;

  @PostMapping("/save")
  public Mono<Object> created(
    @RequestBody @Valid currentAccount model,
    BindingResult bindinResult
  ) {
    String msg = "";

    if (bindinResult.hasErrors()) {
      for (int i = 0; i < bindinResult.getAllErrors().size(); i++) msg =
        bindinResult.getAllErrors().get(0).getDefaultMessage();
      return Mono.just(new message(msg));
    }
    return service.save(model);
  }

  @PostMapping("/movememts")
  public Mono<Object> registedMovememts(
    @RequestBody @Valid movements model,
    BindingResult bindinResult
  ) {
    String msg = "";

    if (bindinResult.hasErrors()) {
      for (int i = 0; i < bindinResult.getAllErrors().size(); i++) msg =
        bindinResult.getAllErrors().get(0).getDefaultMessage();
      return Mono.just(new message(msg));
    }

    return service.saveMovements(model);
  }

  @GetMapping("/")
  public Flux<Object> findAll() {
    return service.getAll();
  }

  @GetMapping("/byNumberAccount/{number}")
  public Mono<Object> findOneByNumberAccount(@PathVariable("number") String number) {
    return service.getOne(number);
  }
  
  @GetMapping("/byCustomer/{id}")
  public Flux<Object> findByCustomer(@PathVariable("id") String id){
	  return service.getByCustomer(id);
  }
}
