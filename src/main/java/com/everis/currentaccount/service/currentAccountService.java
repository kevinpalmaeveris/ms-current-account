package com.everis.currentaccount.service;

import java.text.SimpleDateFormat;
import java.util.*; 
import java.util.concurrent.*; 
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.everis.currentaccount.Constants.Constants;
import com.everis.currentaccount.consumer.webclient;
import com.everis.currentaccount.dto.message;
import com.everis.currentaccount.map.customer;
import com.everis.currentaccount.model.currentAccount;
import com.everis.currentaccount.model.movements;
import com.everis.currentaccount.repository.currentAccountRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Transactional
public class currentAccountService {
	@Autowired
	currentAccountRepository repository;

	private final List<String> operations = Arrays.asList("Retiro", "Deposito", "Trasnferencia", "Comisión");
	private static ScheduledExecutorService executor = null;
	private static final int LIMIT_MOVEMENT = 5;

	private Boolean verifyCustomer(String id) {
		return webclient.customer.get().uri("/verifyId/{id}", id).retrieve().bodyToMono(Boolean.class).block();
	}

	private customer customerFind(String id) {
		return webclient.customer.get().uri("/{id}", id).retrieve().bodyToMono(customer.class).block();
	}

	private Boolean verifyNumberCC(String number) {
		return webclient.currentAccount.get().uri("/verifyByNumberAccount/" + number).retrieve()
				.bodyToMono(Boolean.class).block();
	}

	private Boolean verifyNumberSC(String number) {
		return webclient.savingAccount.get().uri("/verifyByNumberAccount/" + number).retrieve()
				.bodyToMono(Boolean.class).block();
	}

	private Boolean verifyNumberFC(String number) {
		return webclient.fixedAccount.get().uri("/verifyByNumberAccount/" + number).retrieve().bodyToMono(Boolean.class)
				.block();
	}

	private Boolean verifyCR(String number) {
		if (verifyNumberCC(number) || verifyNumberSC(number) || verifyNumberFC(number)) {
			return true;
		}
		return false;
	}

	private String addMovements(movements movement) {
		double val = getAmountByNumber(movement.getAccountEmisor());
		currentAccount model = repository.findByAccountNumber(movement.getAccountEmisor());

		if (movement.getType().equals("Deposito")) {
			model.setAmount(movement.getAmount() + val);
		} else {
			if (movement.getAmount() > val) {
				return Constants.Messages.AMOUNTH_INSUFFICIENT;
			} else {

				if (movement.getType().equals("Trasnferencia") && (movement.getAccountRecep() != null)) {
					if (verifyCR(movement.getAccountRecep())) {
						if (verifyNumberCC(movement.getAccountRecep())) {
							webclient.currentAccount.post().uri("/addTransfer")
									.body(Mono.just(movement), movements.class).retrieve().bodyToMono(Object.class)
									.subscribe();
						}
						if (verifyNumberSC(movement.getAccountRecep())) {
							webclient.savingAccount.post().uri("/addTransfer")
									.body(Mono.just(movement), movements.class).retrieve().bodyToMono(Object.class)
									.subscribe();
						}
						if (verifyNumberFC(movement.getAccountRecep())) {
							webclient.fixedAccount.post().uri("/addTransfer").body(Mono.just(movement), movements.class)
									.retrieve().bodyToMono(Object.class).subscribe();

						}
					} else {
						return Constants.Messages.INCORRECT_DATA;
					}
				}

				model.setAmount(val - movement.getAmount());
			}
		}
		model.getMovements().add(movement);

		repository.save(model);
		return Constants.Messages.SUCCESS_OPERATION;
	}

	private void addCommissionById(String id) {
		double amount = getAmountByID(id) - 1;
		currentAccount model = repository.findById(id).get();

		movements mobj = new movements(model.getAccountNumber(), "Comisión", 1.0);
		model.getMovements().add(mobj);

		model.setAmount(amount);
		repository.save(model);
	}

	private double getAmountByNumber(String number) {
		return repository.findByAccountNumber(number).getAmount();
	}

	private double getAmountByID(String id) {
		return repository.findById(id).get().getAmount();
	}

	private void CommissionForMaintenance(String id) {
		TimerTask _timerTask = new TimerTask() {

			@Override
			public void run() {
				String datestring = new SimpleDateFormat("dd").format(new Date());
				String timestring = new SimpleDateFormat("HH:mm:ss").format(new Date());

				if (datestring.equals("13") && timestring.equals("12:52:20")) {
					addCommissionById(id);
				}

			}
		};
		executor = Executors.newScheduledThreadPool(1);
		executor.scheduleAtFixedRate(_timerTask, 1, 1, TimeUnit.SECONDS);
	}

	public Mono<Object> saveTransfer(movements model) {
		currentAccount obj = repository.findByAccountNumber(model.getAccountRecep());
		double amount = obj.getAmount();

		obj.setAmount(amount + model.getAmount());
		obj.getMovements().add(model);

		repository.save(obj);
		return Mono.just(new message(""));
	}

	public Mono<Object> saveMovements(movements model) {
		String msg = "Movimiento realizado";

		if (repository.existsByAccountNumber(model.getAccountEmisor())) {

			if (!operations.stream().filter(c -> c.equals(model.getType())).collect(Collectors.toList()).isEmpty()) {

				currentAccount obj = repository.findByAccountNumber(model.getAccountEmisor());

				String idaccount = obj.getIdCurrentAccount();
				String profile = obj.getProfile();

				int count = (int) obj.getMovements().stream().count();

				if ((count == 0) || !profile.equals("PYME")) {
					CommissionForMaintenance(idaccount);
				}
				if (count > LIMIT_MOVEMENT) {
					addCommissionById(idaccount);
				}

				msg = addMovements(model);
			} else {
				msg = Constants.Messages.INCORRECT_OPERATION;
			}
		} else {
			msg = Constants.Messages.INCORRECT_DATA;
		}

		return Mono.just(new message(msg));
	}

	public Mono<Object> save(currentAccount model) {
		String msg = Constants.Messages.ACCOUNT_REGISTERED;

		if (verifyCustomer(model.getIdCustomer())) {
			String typeCustomer = customerFind(model.getIdCustomer()).getType();

			if (typeCustomer.equals("empresarial") || !repository.existsByIdCustomer(model.getIdCustomer())) {
				repository.save(model);
			} else {
				msg = Constants.Messages.CLIENT_NO_MORE_ACCOUNT;
			}
		} else {
			msg = Constants.Messages.CLIENT_NOT_REGISTERED;
		}

		return Mono.just(new message(msg));
	}

	public Flux<Object> getAll() {
		return Flux.fromIterable(repository.findAll());
	}

	public Mono<Object> getOne(String id) {
		return Mono.just(repository.findByAccountNumber(id));
	}

	public Mono<Boolean> _verifyByNumberAccount(String number) {
		return Mono.just(repository.existsByAccountNumber(number));
	}

	public Flux<Object> getByCustomer(String id) {
		return Flux.fromIterable(
				repository.findAll().stream().filter(c -> c.getIdCustomer().equals(id)).collect(Collectors.toList()));
	}
}