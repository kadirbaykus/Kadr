package com.student.recipes

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray

private data class Recipe(
    val id: Int,
    val name: String,
    val category: String,
    val calories: Int,
    val ingredients: List<String>
)

class MainActivity : Activity() {
    private val ingredientChecks = mutableListOf<CheckBox>()
    private lateinit var ingredientsContainer: LinearLayout
    private lateinit var resultsContainer: LinearLayout
    private lateinit var recipes: List<Recipe>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ingredientsContainer = findViewById(R.id.ingredientsContainer)
        resultsContainer = findViewById(R.id.resultsContainer)
        val findRecipesButton = findViewById<android.widget.Button>(R.id.findRecipesButton)

        recipes = runCatching { loadRecipes() }.getOrElse {
            showMessage(getString(R.string.recipe_load_error))
            findRecipesButton.isEnabled = false
            return
        }

        addIngredientOptions()
        findRecipesButton.setOnClickListener { showMatchingRecipes() }
        showMessage(getString(R.string.select_ingredients_hint))
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

    private fun addIngredientOptions() {
        val allIngredients = recipes.flatMap { it.ingredients }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

        var currentRow: LinearLayout? = null
        allIngredients.forEachIndexed { index, ingredient ->
            if (index % 2 == 0) {
                currentRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                ingredientsContainer.addView(currentRow)
            }

            val checkBox = CheckBox(this).apply {
                text = ingredient
                textSize = 14f
                setTextColor(getColor(R.color.text_primary))
                minHeight = dp(48)
            }
            ingredientChecks.add(checkBox)
            currentRow?.addView(
                checkBox,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )

            if (index == allIngredients.lastIndex && allIngredients.size % 2 != 0) {
                currentRow?.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
        }
    }

    private fun showMatchingRecipes() {
        val selected = ingredientChecks
            .filter { it.isChecked }
            .map { it.text.toString() }
            .toSet()

        if (selected.isEmpty()) {
            showMessage(getString(R.string.select_at_least_one))
            return
        }

        val matches = recipes.map { recipe ->
            val available = recipe.ingredients.filter { it in selected }
            val missing = recipe.ingredients.filterNot { it in selected }
            RecipeMatch(
                recipe = recipe,
                available = available,
                missing = missing,
                ratio = available.size.toFloat() / recipe.ingredients.size.coerceAtLeast(1)
            )
        }.filter { it.available.isNotEmpty() }
            .sortedWith(
                compareByDescending<RecipeMatch> { it.ratio }
                    .thenBy { it.missing.size }
                    .thenBy { it.recipe.name }
            )

        resultsContainer.removeAllViews()
        if (matches.isEmpty()) {
            showMessage(getString(R.string.no_recipes_found))
            return
        }

        matches.forEach(::addRecipeResult)
    }

    private fun addRecipeResult(match: RecipeMatch) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(getColor(R.color.card_background))
                cornerRadius = dp(6).toFloat()
                setStroke(dp(1), getColor(R.color.divider))
            }
        }
        card.addView(label(match.recipe.name, 18, true, R.color.text_primary))
        card.addView(
            label(
                getString(
                    R.string.recipe_details,
                    match.recipe.category,
                    match.recipe.calories,
                    match.available.size,
                    match.recipe.ingredients.size
                ),
                13,
                false,
                R.color.text_secondary
            )
        )
        card.addView(
            label(
                getString(R.string.available_ingredients, match.recipe.ingredients.joinToString()),
                14,
                false,
                R.color.text_primary
            )
        )

        val status = if (match.missing.isEmpty()) {
            getString(R.string.all_ingredients_available)
        } else {
            getString(R.string.missing_ingredients, match.missing.joinToString())
        }
        val statusColor = if (match.missing.isEmpty()) R.color.text_success else R.color.text_missing
        card.addView(label(status, 14, match.missing.isEmpty(), statusColor))

        resultsContainer.addView(
            card,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        )
    }

    private fun showMessage(message: String) {
        resultsContainer.removeAllViews()
        resultsContainer.addView(label(message, 15, false, R.color.text_secondary).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(20), dp(12), dp(20))
        })
    }

    private fun label(text: String, size: Int, bold: Boolean, color: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(getColor(color))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(3), 0, dp(3))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class RecipeMatch(
        val recipe: Recipe,
        val available: List<String>,
        val missing: List<String>,
        val ratio: Float
    )
}