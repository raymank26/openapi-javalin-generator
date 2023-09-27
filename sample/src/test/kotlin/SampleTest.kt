import foo.*
import io.javalin.Javalin
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

private val cat = Pet(1, "Cat", "orange")
private val dog = Pet(2, "Dog", "black")

class SampleTest : BeforeAllCallback {

    private val petClinicClient = Client("http://localhost:8080")

    companion object {

        private val javalin: Javalin = Javalin.create()
        private val petController = JavalinController(PetServer())

        @JvmStatic
        @BeforeAll
        fun start() {
            petController.bind(javalin)
            javalin.start(8080)
        }
    }

    override fun beforeAll(context: ExtensionContext?) {
        TODO("Not yet implemented")
    }

    @Test
    fun shouldListPets() {
        val petsResponse = petClinicClient.listPets(10)

        Assertions.assertEquals(
            Pets(listOf(cat, dog)),
            (petsResponse as ListPetsResponse.Pets).pets
        )
    }

    @Test
    fun shouldReceiveError() {
        val petsResponse = petClinicClient.listPets(-1)

        Assertions.assertEquals(Error(400, "Limit <= 0"), (petsResponse as ListPetsResponse.Error).error)
    }
}

private val pets = listOf(cat, dog).associateBy { it.id }

class PetServer : Server {

    override fun listPets(limit: Int): ListPetsResponse {
        return if (limit > 0) {
            ListPetsResponse.Pets(Pets(pets.values.toList().take(limit)))
        } else {
            ListPetsResponse.Error(Error(400, "Limit <= 0"))
        }
    }

    override fun showPetById(petId: String): ShowPetByIdResponse {
        val pet = pets[petId.toInt()]
        return if (pet != null) {
            ShowPetByIdResponse.Pet(pet)
        } else {
            ShowPetByIdResponse.Error(Error(404, "Not found"))
        }
    }
}