package com.github.raymank26

import foo.JavalinController
import foo.ListPetsResponse
import foo.Server
import foo.ShowPetByIdResponse
import io.javalin.Javalin

object Sample {

    @JvmStatic
    fun main(args: Array<String>) {
        val javalinController = JavalinController(PetServer())
        val javalin = Javalin.create()
        javalinController.bind(javalin)

        javalin.start(8080)
    }
}

class PetServer : Server {
    override fun listPets(limit: Int): ListPetsResponse {
        TODO("Not yet implemented")
    }

    override fun showPetById(petId: String): ShowPetByIdResponse {
        TODO("Not yet implemented")
    }
}