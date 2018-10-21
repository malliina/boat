package com.malliina.boat

abstract class Lang(val name: String,
                    val location: String,
                    val `type`: String,
                    val navigation: String,
                    val construction: String,
                    val speed: String,
                    val water: String,
                    val depth: String)

object Finnish extends Lang("Nimi", "Sijainti", "Tyyppi", "Navigointi", "Rakenne", "Nopeus", "Vesi", "Syvyys")

object Swedish extends Lang("Namn", "Plats", "Typ", "Navigering", "Struktur", "Hastighet", "Vatten", "Djup")
