package io.qalipsis.core.head.security.auth0

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.micronaut.http.client.exceptions.EmptyResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.test.coroutines.TestDispatcherProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.random.Random

/**
 * @author pbril
 */
@MicronautTest
internal class Auth0IdentityManagementTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private lateinit var identityManagement: Auth0IdentityManagement

    private val userPrototype = UserEntity(
        username = "auth-user${(Random.nextInt(-9999, 99999))}",
        displayName = "test",
        emailAddress = "foo+${(Random.nextInt(10, 36000))}@bar.com"
    )

    @BeforeAll
    fun setUp() {
        identityManagement = Auth0IdentityManagement(object : Auth0Configuration {
            override val connection: String = "qalipsis"
            override val token: String =
                "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IjkwanNlanptNHlWZXVjaDgxU2NMWiJ9.eyJpc3MiOiJodHRwczovL2Rldi1kN3hlNDktMS51cy5hdXRoMC5jb20vIiwic3ViIjoiYjNIbHlsRUxkQkY0NjhoaGVUVGNqZ01za1BmR1c1R0NAY2xpZW50cyIsImF1ZCI6Imh0dHBzOi8vZGV2LWQ3eGU0OS0xLnVzLmF1dGgwLmNvbS9hcGkvdjIvIiwiaWF0IjoxNjUwODMxNDQ5LCJleHAiOjE2NTA5MTc4NDksImF6cCI6ImIzSGx5bEVMZEJGNDY4aGhlVFRjamdNc2tQZkdXNUdDIiwic2NvcGUiOiJyZWFkOmNsaWVudF9ncmFudHMgY3JlYXRlOmNsaWVudF9ncmFudHMgZGVsZXRlOmNsaWVudF9ncmFudHMgdXBkYXRlOmNsaWVudF9ncmFudHMgcmVhZDp1c2VycyB1cGRhdGU6dXNlcnMgZGVsZXRlOnVzZXJzIGNyZWF0ZTp1c2VycyByZWFkOnVzZXJzX2FwcF9tZXRhZGF0YSB1cGRhdGU6dXNlcnNfYXBwX21ldGFkYXRhIGRlbGV0ZTp1c2Vyc19hcHBfbWV0YWRhdGEgY3JlYXRlOnVzZXJzX2FwcF9tZXRhZGF0YSByZWFkOnVzZXJfY3VzdG9tX2Jsb2NrcyBjcmVhdGU6dXNlcl9jdXN0b21fYmxvY2tzIGRlbGV0ZTp1c2VyX2N1c3RvbV9ibG9ja3MgY3JlYXRlOnVzZXJfdGlja2V0cyByZWFkOmNsaWVudHMgdXBkYXRlOmNsaWVudHMgZGVsZXRlOmNsaWVudHMgY3JlYXRlOmNsaWVudHMgcmVhZDpjbGllbnRfa2V5cyB1cGRhdGU6Y2xpZW50X2tleXMgZGVsZXRlOmNsaWVudF9rZXlzIGNyZWF0ZTpjbGllbnRfa2V5cyByZWFkOmNvbm5lY3Rpb25zIHVwZGF0ZTpjb25uZWN0aW9ucyBkZWxldGU6Y29ubmVjdGlvbnMgY3JlYXRlOmNvbm5lY3Rpb25zIHJlYWQ6cmVzb3VyY2Vfc2VydmVycyB1cGRhdGU6cmVzb3VyY2Vfc2VydmVycyBkZWxldGU6cmVzb3VyY2Vfc2VydmVycyBjcmVhdGU6cmVzb3VyY2Vfc2VydmVycyByZWFkOmRldmljZV9jcmVkZW50aWFscyB1cGRhdGU6ZGV2aWNlX2NyZWRlbnRpYWxzIGRlbGV0ZTpkZXZpY2VfY3JlZGVudGlhbHMgY3JlYXRlOmRldmljZV9jcmVkZW50aWFscyByZWFkOnJ1bGVzIHVwZGF0ZTpydWxlcyBkZWxldGU6cnVsZXMgY3JlYXRlOnJ1bGVzIHJlYWQ6cnVsZXNfY29uZmlncyB1cGRhdGU6cnVsZXNfY29uZmlncyBkZWxldGU6cnVsZXNfY29uZmlncyByZWFkOmhvb2tzIHVwZGF0ZTpob29rcyBkZWxldGU6aG9va3MgY3JlYXRlOmhvb2tzIHJlYWQ6YWN0aW9ucyB1cGRhdGU6YWN0aW9ucyBkZWxldGU6YWN0aW9ucyBjcmVhdGU6YWN0aW9ucyByZWFkOmVtYWlsX3Byb3ZpZGVyIHVwZGF0ZTplbWFpbF9wcm92aWRlciBkZWxldGU6ZW1haWxfcHJvdmlkZXIgY3JlYXRlOmVtYWlsX3Byb3ZpZGVyIGJsYWNrbGlzdDp0b2tlbnMgcmVhZDpzdGF0cyByZWFkOmluc2lnaHRzIHJlYWQ6dGVuYW50X3NldHRpbmdzIHVwZGF0ZTp0ZW5hbnRfc2V0dGluZ3MgcmVhZDpsb2dzIHJlYWQ6bG9nc191c2VycyByZWFkOnNoaWVsZHMgY3JlYXRlOnNoaWVsZHMgdXBkYXRlOnNoaWVsZHMgZGVsZXRlOnNoaWVsZHMgcmVhZDphbm9tYWx5X2Jsb2NrcyBkZWxldGU6YW5vbWFseV9ibG9ja3MgdXBkYXRlOnRyaWdnZXJzIHJlYWQ6dHJpZ2dlcnMgcmVhZDpncmFudHMgZGVsZXRlOmdyYW50cyByZWFkOmd1YXJkaWFuX2ZhY3RvcnMgdXBkYXRlOmd1YXJkaWFuX2ZhY3RvcnMgcmVhZDpndWFyZGlhbl9lbnJvbGxtZW50cyBkZWxldGU6Z3VhcmRpYW5fZW5yb2xsbWVudHMgY3JlYXRlOmd1YXJkaWFuX2Vucm9sbG1lbnRfdGlja2V0cyByZWFkOnVzZXJfaWRwX3Rva2VucyBjcmVhdGU6cGFzc3dvcmRzX2NoZWNraW5nX2pvYiBkZWxldGU6cGFzc3dvcmRzX2NoZWNraW5nX2pvYiByZWFkOmN1c3RvbV9kb21haW5zIGRlbGV0ZTpjdXN0b21fZG9tYWlucyBjcmVhdGU6Y3VzdG9tX2RvbWFpbnMgdXBkYXRlOmN1c3RvbV9kb21haW5zIHJlYWQ6ZW1haWxfdGVtcGxhdGVzIGNyZWF0ZTplbWFpbF90ZW1wbGF0ZXMgdXBkYXRlOmVtYWlsX3RlbXBsYXRlcyByZWFkOm1mYV9wb2xpY2llcyB1cGRhdGU6bWZhX3BvbGljaWVzIHJlYWQ6cm9sZXMgY3JlYXRlOnJvbGVzIGRlbGV0ZTpyb2xlcyB1cGRhdGU6cm9sZXMgcmVhZDpwcm9tcHRzIHVwZGF0ZTpwcm9tcHRzIHJlYWQ6YnJhbmRpbmcgdXBkYXRlOmJyYW5kaW5nIGRlbGV0ZTpicmFuZGluZyByZWFkOmxvZ19zdHJlYW1zIGNyZWF0ZTpsb2dfc3RyZWFtcyBkZWxldGU6bG9nX3N0cmVhbXMgdXBkYXRlOmxvZ19zdHJlYW1zIGNyZWF0ZTpzaWduaW5nX2tleXMgcmVhZDpzaWduaW5nX2tleXMgdXBkYXRlOnNpZ25pbmdfa2V5cyByZWFkOmxpbWl0cyB1cGRhdGU6bGltaXRzIGNyZWF0ZTpyb2xlX21lbWJlcnMgcmVhZDpyb2xlX21lbWJlcnMgZGVsZXRlOnJvbGVfbWVtYmVycyByZWFkOmVudGl0bGVtZW50cyByZWFkOmF0dGFja19wcm90ZWN0aW9uIHVwZGF0ZTphdHRhY2tfcHJvdGVjdGlvbiByZWFkOm9yZ2FuaXphdGlvbnNfc3VtbWFyeSByZWFkOm9yZ2FuaXphdGlvbnMgdXBkYXRlOm9yZ2FuaXphdGlvbnMgY3JlYXRlOm9yZ2FuaXphdGlvbnMgZGVsZXRlOm9yZ2FuaXphdGlvbnMgY3JlYXRlOm9yZ2FuaXphdGlvbl9tZW1iZXJzIHJlYWQ6b3JnYW5pemF0aW9uX21lbWJlcnMgZGVsZXRlOm9yZ2FuaXphdGlvbl9tZW1iZXJzIGNyZWF0ZTpvcmdhbml6YXRpb25fY29ubmVjdGlvbnMgcmVhZDpvcmdhbml6YXRpb25fY29ubmVjdGlvbnMgdXBkYXRlOm9yZ2FuaXphdGlvbl9jb25uZWN0aW9ucyBkZWxldGU6b3JnYW5pemF0aW9uX2Nvbm5lY3Rpb25zIGNyZWF0ZTpvcmdhbml6YXRpb25fbWVtYmVyX3JvbGVzIHJlYWQ6b3JnYW5pemF0aW9uX21lbWJlcl9yb2xlcyBkZWxldGU6b3JnYW5pemF0aW9uX21lbWJlcl9yb2xlcyBjcmVhdGU6b3JnYW5pemF0aW9uX2ludml0YXRpb25zIHJlYWQ6b3JnYW5pemF0aW9uX2ludml0YXRpb25zIGRlbGV0ZTpvcmdhbml6YXRpb25faW52aXRhdGlvbnMiLCJndHkiOiJjbGllbnQtY3JlZGVudGlhbHMifQ.pth_ZYC7IM0HJS777Iz1LbvC6q_w7tbw6QqoeMDrhh24XK9SsgQxOHXeKH2406mlnEBs_DF-xvTxbY8gnwM5TRoFGiu1Pky7MteR-kn_QLQaFhtC2JkWSmUuOvGpTyltoi7syKzIjCk-EIFq5J2nAFanthmRpVztbe6mLYre8YDKiSw8JVEYZW6ZznAgXJaAAlP8kYuk8ZawDjAwO-4TuTEHCO6pBrZuHqJT_95P6Q-GL7E2bywjmFcxfg-cyQwGUxmJxGAtV49CVNQ9-aYiLk8VUdqhHOLZnNUtHytXF8CCSAcgmaXvtqSUWBJXdCtoIeOTkLQzs2wcv0t86kgSOQ"
            override val baseAddress: String = "https://dev-d7xe49-1.us.auth0.com/api/v2/users"
            override val clientId: String = ""
            override val clientSecret: String = ""
            override val apiIdentifier: String = ""
        }
        )
    }

    @Test
    fun `should save and get user from auth0`() = testDispatcherProvider.run {
        // when
        val result =
            identityManagement.save(userPrototype.copy(emailAddress = "foo+${(Random.nextInt(10, 36000))}@bar.com"))

        //  then
        assertThat(result.identityReference).isNotNull()

        val result2 = identityManagement.get(result.identityReference!!)
        assertThat(result2.username).isEqualTo(result.username)
        assertThat(result2.emailAddress).isEqualTo(result.emailAddress)
        assertThat(result2.displayName).isEqualTo(result.displayName)

        identityManagement.delete(result.identityReference!!)
    }

    @Test
    fun `should update user from auth0`() = testDispatcherProvider.run {
        // when
        val result =
            identityManagement.save(userPrototype.copy(emailAddress = "foo+${(Random.nextInt(10, 36000))}@bar.com"))
        identityManagement.update(result.copy(username = "new-qalipsis"))

        //  then
        val result2 = identityManagement.get(result.identityReference!!)
        assertThat(result2.username).isEqualTo("new-qalipsis")

        identityManagement.delete(result.identityReference!!)
    }

    @Test
    fun `should delete user from auth0`() = testDispatcherProvider.run {
        // when
        val result =
            identityManagement.save(userPrototype.copy(emailAddress = "foo+${(Random.nextInt(10, 36000))}@bar.com"))
        identityManagement.delete(result.identityReference!!)

        //  then
        assertThrows<EmptyResponseException> {
            identityManagement.get(result.identityReference!!)
        }
    }
}