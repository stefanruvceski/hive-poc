package app.stride

import app.stride.habits.HabitsModule
import app.stride.money.MoneyModule
import app.stride.books.BooksModule
import app.stride.draw.DrawModule
import app.stride.host.AppModule

/** The modules in this app, in home-screen order. Add a new one here (and as a Gradle module in settings.gradle.kts). */
object Modules {
    val all: List<AppModule> = listOf(HealthModule, HabitsModule, MoneyModule, BooksModule, DrawModule)
}
