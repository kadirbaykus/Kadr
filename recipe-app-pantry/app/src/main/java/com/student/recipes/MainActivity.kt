package com.student.recipes

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray

data class Recipe(
    val id: Int,
    val name: String,
    val category: String,
    val calories: Int,
    val ingredients: List<String>
)

class MainActivity : Activity() {
    private val recipes by lazy { loadRecipes() }
    private val ingredientChecks = mutableListOf<CheckBox>()
    private lateinit var resultsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val scrollView = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(content)
        page.addView(
            scrollView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(page)

        content.addView(label("Kilerimdeki Malzemeler", 22, true))
        content.addView(label("Elindeki malzemeleri seç, sana uygun tarifleri bulalım.", 15))

        val allIngredients = recipes.flatMap { it.ingredients }
            .distinct()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })

        allIngredients.forEach { ingredient ->
            val checkBox = CheckBox(this).apply {
                text = ingredient
                textSize = 16f
            }
            ingredientChecks.add(checkBox)
            content.addView(checkBox)
        }

        content.addView(Button(this).apply {
            text = "Tarifleri Bul"
            setOnClickListener { showMatchingRecipes() }
        })
        content.addView(label("Tarif Önerileri", 20, true))
        resultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(resultsContainer)
        showMessage("Tarifleri görmek için malzemelerini seç.")
    }

    private fun loadRecipes(): List<Recipe> {
        val json = assets.open("recipes.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val ingredients = item.getJSONArray("ingredients")
            Recipe(
                id = item.getInt("id"),
                name = item.getString("name"),
                category = item.getString("category"),
                calories = item.getInt("cal"),
                ingredients = (0 until ingredients.length()).map { ingredientIndex ->
                    ingredients.getString(ingredientIndex)
                }
            )
        }
    }

    private fun showMatchingRecipes() {
        val selected = ingredientChecks.filter { it.isChecked }.map { it.text.toString() }.toSet()
        if (selected.isEmpty()) {
            showMessage("Önce en az bir malzeme seç.")
            return
        }

        val matches = recipes.map { recipe ->
            val available = recipe.ingredients.filter { it in selected }
            val missing = recipe.ingredients.filterNot { it in selected }
            Match(recipe, available, missing, available.size.toFloat() / recipe.ingredients.size)
        }.filter { it.available.isNotEmpty() }
            .sortedWith(compareByDescending<Match> { it.ratio }.thenBy { it.missing.size })

        resultsContainer.removeAllViews()
        if (matches.isEmpty()) {
            showMessage("Seçtiğin malzemelerle eşleşen tarif bulunamadı.")
            return
        }

        matches.forEach { match ->
            resultsContainer.addView(label(match.recipe.name, 18, true))
            resultsContainer.addView(
                label("${match.recipe.category} · ${match.recipe.calories} kcal · ${match.available.size}/${match.recipe.ingredients.size} malzeme", 14)
            )
            resultsContainer.addView(label("Malzemeler: ${match.recipe.ingredients.joinToString()}", 14))
            val missingText = if (match.missing.isEmpty()) {
                "Tüm malzemeler elinde var."
            } else {
                "Eksik: ${match.missing.joinToString()}"
            }
            resultsContainer.addView(label(missingText, 14))
            resultsContainer.addView(label("", 8))
        }
    }

    private fun showMessage(message: String) {
        resultsContainer.removeAllViews()
        resultsContainer.addView(label(message, 15))
    }

    private fun label(text: String, size: Int, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class Match(
        val recipe: Recipe,
        val available: List<String>,
        val missing: List<String>,
        val ratio: Float
    )
}