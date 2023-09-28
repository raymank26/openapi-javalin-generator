import foo.*
import io.javalin.Javalin
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private val cat = Pet(1, "Cat", "orange")
private val dog = Pet(2, "Dog", "black")

class SampleTest {

    private val petClinicClient = Client("http://localhost:8080")

    companion object {

        private val javalin: Javalin = Javalin.create()
        private val petServer = PetServer()
        private val petController = JavalinController(petServer)

        @JvmStatic
        @BeforeAll
        fun start() {
            petController.bind(javalin)
            javalin.start(8080)
        }
    }

    @BeforeEach
    fun cleanup() {
        petServer.reInit()
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

    @Test
    fun shouldCreatePet() {
        // given
        val pet = Pet(5, "Tiger", "animal")

        // when
        val createResponse = petClinicClient.createPet(CreatePetRequest.Form(pet))
        val showResponse = petClinicClient.showPetById(5.toString()) as ShowPetByIdResponse.Pet

        Assertions.assertInstanceOf(CreatePetResponse.Created::class.java, createResponse)

        // then
        Assertions.assertEquals(pet, showResponse.pet)
    }
}

class PetServer : Server {

    private lateinit var pets: MutableMap<Int, Pet>

    init {
        reInit()
    }

    override fun listPets(limit: Int): ListPetsResponse {
        return if (limit > 0) {
            ListPetsResponse.Pets(Pets(pets.values.toList().take(limit)))
        } else {
            ListPetsResponse.Error(Error(400, "Limit <= 0"))
        }
    }

    override fun createPet(requestBody: CreatePetRequest): CreatePetResponse {
        val pet = (requestBody as CreatePetRequest.Form).pet
        pets[pet.id] = pet
        return CreatePetResponse.Created
    }

    override fun showPetById(petId: String): ShowPetByIdResponse {
        val pet = pets[petId.toInt()]
        return if (pet != null) {
            ShowPetByIdResponse.Pet(pet)
        } else {
            ShowPetByIdResponse.Error(Error(404, "Not found"))
        }
    }

    fun reInit() {
        pets = mutableListOf(cat, dog).associateBy { it.id }.toMutableMap()
    }
}