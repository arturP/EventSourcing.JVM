package io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app.api.controllers;

import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app.api.ShoppingCartsRequests;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.core.entities.CommandHandler;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.core.http.ETag;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app.shoppingcarts.*;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app.shoppingcarts.productitems.ProductItem;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import static com.eventstore.dbclient.ExpectedRevision.noStream;
import static com.eventstore.dbclient.ExpectedRevision.expectedRevision;
import static io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app.shoppingcarts.ShoppingCartCommand.*;

@Validated
@RestController
@RequestMapping("api/shopping-carts")
class ShoppingCartsController {
  private final CommandHandler<ShoppingCart, ShoppingCartCommand, ShoppingCartEvent> store;

  ShoppingCartsController(
    CommandHandler<ShoppingCart, ShoppingCartCommand, ShoppingCartEvent> store
  ) {
    this.store = store;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ResponseEntity<Void> open(
    @Valid @RequestBody ShoppingCartsRequests.Open request
  ) throws URISyntaxException {
    var cartId = UUID.randomUUID();

    var result = store.handle(
      cartId,
      new OpenShoppingCart(
        cartId,
        request.clientId()
      ),
      noStream()
    );

    return ResponseEntity
      .created(new URI("api/shopping-carts/%s".formatted(cartId)))
      .eTag(result.value())
      .build();
  }

  @PostMapping("{id}/products")
  ResponseEntity<Void> addProduct(
    @PathVariable UUID id,
    @RequestBody ShoppingCartsRequests.AddProduct request,
    @RequestHeader(name = HttpHeaders.IF_MATCH) @Parameter(in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string")) @NotNull ETag ifMatch
  ) {
    if (request.productItem() == null)
      throw new IllegalArgumentException("Product Item has to be defined");

    var result = store.handle(
      id,
      new AddProductItemToShoppingCart(
        id,
        new ProductItem(
          request.productItem().productId(),
          request.productItem().quantity()
        )
      ),
      expectedRevision(ifMatch.toLong())
    );

    return ResponseEntity
      .ok()
      .eTag(result.value())
      .build();
  }

  @DeleteMapping("{id}/products/{productId}")
  ResponseEntity<Void> removeProduct(
    @PathVariable UUID id,
    @PathVariable UUID productId,
    @RequestParam @NotNull Integer quantity,
    @RequestParam @NotNull Double price,
    @RequestHeader(name = HttpHeaders.IF_MATCH) @Parameter(in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string")) @NotNull ETag ifMatch
  ) {
    var result = store.handle(
      id,
      new RemoveProductItemFromShoppingCart(
        id,
        new PricedProductItem(
          new ProductItem(
            productId,
            quantity
          ),
          price
        )
      ),
      expectedRevision(ifMatch.toLong())
    );

    return ResponseEntity
      .ok()
      .eTag(result.value())
      .build();
  }

  @PutMapping("{id}")
  ResponseEntity<Void> confirmCart(
    @PathVariable UUID id,
    @RequestHeader(name = HttpHeaders.IF_MATCH) @Parameter(in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string")) @NotNull ETag ifMatch
  ) {
    var result = store.handle(
      id,
      new ConfirmShoppingCart(id),
      expectedRevision(ifMatch.toLong())
    );

    return ResponseEntity
      .ok()
      .eTag(result.value())
      .build();
  }

  @DeleteMapping("{id}")
  ResponseEntity<Void> cancelCart(
    @PathVariable UUID id,
    @RequestHeader(name = HttpHeaders.IF_MATCH) @Parameter(in = ParameterIn.HEADER, required = true, schema = @Schema(type = "string")) @NotNull ETag ifMatch
  ) {
    var result = store.handle(
      id,
      new CancelShoppingCart(id),
      expectedRevision(ifMatch.toLong())
    );

    return ResponseEntity
      .ok()
      .eTag(result.value())
      .build();
  }

//  @GetMapping("{id}")
//  ResponseEntity<ShoppingCart> getById(
//    @PathVariable UUID id,
//    @RequestHeader(name = HttpHeaders.IF_NONE_MATCH) @Parameter(in = ParameterIn.HEADER, schema = @Schema(type = "string")) @Nullable ETag ifNoneMatch
//  ) {
//    var result = handle(detailsRepository, new GetShoppingCartById(id, ifNoneMatch));
//
//    return ResponseEntity
//      .ok()
//      .eTag(ETag.weak(result.getVersion()).value())
//      .body(result);
//  }
}
