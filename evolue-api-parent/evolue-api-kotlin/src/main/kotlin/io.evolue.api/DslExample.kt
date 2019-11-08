package io.evolue.api

import assertk.assertThat
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotEmpty
import io.evolue.api.kotlin.components.AssertionDataSource
import io.evolue.api.kotlin.components.Request
import io.evolue.api.kotlin.components.Response
import io.evolue.api.kotlin.components.TestDataSource
import java.time.Duration
import kotlin.reflect.KClass


class MyScenario : ScenarioSpec("add to cart", {

    action("Load Cart")
            .with(CsvTestDataSource<User>("gcs://repository/users.csv"))
            .request { user, context ->
                httpGet("https://api.shop.com/${user.id}/cart")
            }
            .check { it.isOk() }
            .entity { asJson(it.body(), Cart::class) }
            .map { user, cart ->
                cart.items
            }
            .combineAndThen("Add to cart", CsvTestDataSource<Product>("gcs://repository/products.csv")) {
                request { (items, product), context ->
                    println("Selecting ${product.name} of ${items.size} items")
                    httpPost("https://api.shop.com/cart", """
                        {
                                    "product":${product.id},
                                    "quantity": 1
                                }
                    """.trimIndent())
                }
                        .check { it.isOk() }
            }
            .correlate("Product should be added to the cart") {
                with(KafkaAssertionDataSource<AddToCartEvent>(listOf("orders"), "bootstrap" to "", "" to ""))
                        .key { event -> event?.userId }
                        .actionKey { user, reponse, items ->
                            user?.id
                        }
                        .assert {
                            timeout(Duration.ofMillis(3000))
                                    .verify { (user, event), response, items, metrics ->
                                        assertThat(metrics.duration).isLessThanOrEqualTo(Duration.ofMillis(300))
                                        assertThat(items).isNotEmpty()
                                    }
                                    .map { (user, event), response, items ->
                                        items.size
                                    }.combineAndThen("Pay", CsvTestDataSource<CreditCard>("gcs://repository/creditcards.csv")) {
                                        request { (size, card), context ->
                                            httpPost("", """
                                                
                                            """.trimIndent())
                                        }
                                                .check { it.isOk() }
                                    }
                        }
            }
            .then("Have a coffee") {
                request { _, _ ->
                    AnyRequest("Aaaaahhhhh!!!")
                }
                        .check { it.isGood() }
            }
            .assert("User's cart should be loaded") {
                timeout(Duration.ofMillis(3000))
                        .verify { user, response, items, metrics ->
                            assertThat(metrics.duration).isLessThanOrEqualTo(Duration.ofMillis(300))
                            assertThat(items).isNotEmpty()
                        }
                        // Here is the lottery!
                        .map { user, httpResponse, items -> Math.random() }
                        .then("Did I win?") {
                            request { randomNumber, context ->
                                httpGet("http://check-if-i-won.com/${randomNumber}")
                            }
                        }
            }


})


// Export from the file

class CsvTestDataSource<O>(path: String) : TestDataSource<O> {
    override suspend fun reset() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun hasNext(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun next(): O {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class KafkaAssertionDataSource<O>(topics: List<String>, vararg configuration: Pair<String, String>) : AssertionDataSource<O> {
    override suspend fun hasNext(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun next(): O {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun reset() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class HttpGetRequest(url: String) : Request<HttpResponse> {
    override suspend fun execute(): HttpResponse {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class HttpPostRequest(url: String, body: String) : Request<HttpResponse> {
    override suspend fun execute(): HttpResponse {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class HttpResponse : Response<ByteArray> {
    fun isOk(): Boolean = true
    fun body() = ByteArray(1)
}

class AnyRequest<U>(val value: U) : Request<AnyResponse<U>> {
    override suspend fun execute() = AnyResponse(value)
}

class AnyResponse<U>(val value: U) : Response<U> {

    fun isGood(): Boolean = (this.value != null)

    fun get() = value
}

data class User(val id: Long, val name: String)
data class Item(val productId: Long, val quantity: Int)
data class Cart(val items: List<Item>)
data class Product(val id: Long, val name: String)
data class CreditCard(val id: Long, val name: String)
data class AddToCartEvent(val userId: Long, val productId: Long, val quantity: Int)
data class UserCart(val cart: Cart, val user: User)

fun httpGet(url: String) = HttpGetRequest(url)
fun httpPost(url: String, body: String) = HttpPostRequest(url, body)
fun <T : Any> asJson(input: ByteArray, entityClass: KClass<T>): T = null!!