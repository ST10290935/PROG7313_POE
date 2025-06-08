//Code Attribution:
//For the code below these are the sources I have used to improve my knowledge and implement features:
//Stevdza-San, 2020. ROOM Database - #1 Create Database Schema | Android Studio Tutorial.[video online] Available at: <https://youtu.be/lwAvI3WDXBY?si=mq1S9X37wiOx5aX5> [Accessed 1 April 2025].
//AndroidWithShiv, 2023. Room Database | CRUD Operation in ROOM DB | Android Studio | JAVA | #android #java #database.[online video] Available at: <https://youtu.be/r-ua6f6LmJA?si=NtFoH1Z_Mng2GYrT> [Accessed 1 April 2025].
//Coding Meet, 2023. How to Store and Retrieve Images in Room Database | Android Studio Kotlin Tutorial.[online video] Available at: <https://youtu.be/0NVm3uVRNzg?si=n_sPPOCgiC0pQ8do> [Accessed 4 April 2025].
// how to videos, 2022. Insert image into database and retrieve in navigation drawer (android studio)(android tutorials).[online video] Available at: <https://youtu.be/8_LuejJEF7o?si=Hhr8a8QOxmGuFaAV> [Accessed 4 April 2025].
// Android Knowledge, 2023. RecyclerView in Android Studio using Kotlin | Android Knowledge.[online video] Available at: <https://youtu.be/UDfyZLWyyVM?si=XkKwy4-9apD5AZcW> [Accessed 7 April 2025].



package com.fake.cashflow.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.fake.cashflow.R
import com.fake.cashflow.data.AppDatabase
import com.fake.cashflow.data.Category
import com.fake.cashflow.data.Expense
import com.fake.cashflow.firebase.FirebaseDataSync
import com.fake.cashflow.viewmodel.ExpenseViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.util.Log
import android.widget.ImageView

class AddExpenseActivity : AppCompatActivity() {
    private lateinit var etAmount: EditText
    private lateinit var etDescription: EditText
    private lateinit var btnSelectDate: Button
    private lateinit var tvSelectedDate: TextView
    private lateinit var spinnerCategory: Spinner
    private lateinit var btnSave: Button
    private lateinit var expenseViewModel: ExpenseViewModel

    private lateinit var btnBack: Button

    private lateinit var btnAddPhoto: Button
    private val PICK_IMAGE_REQUEST = 1
    //for camera
    private val REQUEST_IMAGE_CAPTURE = 2
    private var cameraImageUri: Uri? = null

    private var selectedImageUri: Uri? = null



    // Holds the chosen date in milliseconds; Default is current time.
    private var selectedDateInMillis: Long = Date().time
    private var selectedCategoryId: Long? = null
    private var categories = listOf<Category>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_expense)

        etAmount = findViewById(R.id.etAmount)
        etDescription = findViewById(R.id.etDescription)
        btnSelectDate = findViewById(R.id.btnSelectDate)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        btnSave = findViewById(R.id.btnSave)
        btnBack = findViewById(R.id.btnBack)
        btnAddPhoto = findViewById(R.id.btnAddPhoto)




        expenseViewModel = ViewModelProvider(this).get(ExpenseViewModel::class.java)

        // Set initial date display.
        updateSelectedDateDisplay(selectedDateInMillis)

        // Set up the categories spinner
        setupCategorySpinner()

        // Set up the date picker.
        btnSelectDate.setOnClickListener {
            val calendar = Calendar.getInstance().apply { timeInMillis = selectedDateInMillis }
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(
                this,
                { _, selectedYear, selectedMonth, selectedDay ->
                    val newCal = Calendar.getInstance().apply {
                        set(selectedYear, selectedMonth, selectedDay)
                    }
                    selectedDateInMillis = newCal.timeInMillis
                    updateSelectedDateDisplay(selectedDateInMillis)
                },
                year, month, day
            )
            datePickerDialog.show()
        }

        btnSave.setOnClickListener {
            val amountText = etAmount.text.toString().trim()
            val description = etDescription.text.toString().trim()
            if (amountText.isEmpty() || description.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val amount = try {
                amountText.toDoubleOrNull() ?: run {
                    Toast.makeText(this, "Invalid amount format", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val firebaseAuth = FirebaseAuth.getInstance()
            val currentUser = firebaseAuth.currentUser
            val currentUserId = currentUser?.uid
            if (currentUserId == null) {
                Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return@setOnClickListener
            }

            // Create an Expense instance with the selected date and category.
            var base64Image: String? = null

            // Convert image to base64 if selected
            if (selectedImageUri != null) {
                try {
                    Log.d("AddExpenseActivity", "Converting image to base64: $selectedImageUri")

                    val inputStream = contentResolver.openInputStream(selectedImageUri!!)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()

                    if (bytes != null) {
                        // Compress the image before converting to base64
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val compressedBitmap = compressImage(bitmap)

                        // Convert to base64
                        val byteArrayOutputStream = java.io.ByteArrayOutputStream()
                        compressedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream)
                        val byteArray = byteArrayOutputStream.toByteArray()
                        base64Image = android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)

                        Log.d("AddExpenseActivity", "Image converted to base64 (length: ${base64Image.length})")
                    }
                } catch (e: Exception) {
                    Log.e("AddExpenseActivity", "Error converting image to base64", e)
                    Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d("AddExpenseActivity", "No image selected")
            }

            val expense = Expense(
                date = selectedDateInMillis,
                amount = amount,
                description = description,
                userId = currentUserId,
                categoryId = selectedCategoryId,
                imagePath = base64Image
            )
            expenseViewModel.insertExpense(expense) { id ->
                // Save to Firebase
                val db = AppDatabase.getInstance(this@AddExpenseActivity)
                val dataSync = FirebaseDataSync.getInstance(db)

                // Sync the expense to Firebase
                lifecycleScope.launch {
                    try {
                        dataSync.syncExpensesToFirebase(currentUserId)
                        Toast.makeText(this@AddExpenseActivity, "Expense saved and synced", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@AddExpenseActivity, "Expense saved locally", Toast.LENGTH_SHORT).show()
                    } finally {
                        finish()
                    }
                }
            }
        }
        btnBack.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        btnAddPhoto.setOnClickListener {
            val options = arrayOf("Choose from Gallery", "Take Photo")
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle("Attach Image")
            builder.setItems(options) { _, which ->
                when (which) {
                    0 -> openImageChooser()
                    1 -> openCamera()
                }
            }
            builder.show()
        }





    }

    //camera attempt
    private fun openCamera() {
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        val imageFile = createImageFile()
        cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider", // Ensure this matches your AndroidManifest
            imageFile
        )
        intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraImageUri)
        startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
    }

    private fun createImageFile(): java.io.File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = cacheDir
        return java.io.File.createTempFile("JPEG_${timestamp}_", ".jpg", storageDir)
    }

    private fun setupCategorySpinner() {
        // Observe categories from ViewModel
        expenseViewModel.categories.observe(this) { categoryList ->
            categories = categoryList

            // Create a list of category names for the spinner
            val categoryNames = categoryList.map { it.name }.toMutableList()

            // Add "No Category" option at the beginning
            categoryNames.add(0, "No Category")

            // Create and set up adapter
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                categoryNames
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCategory.adapter = adapter

            // Set spinner selection listener
            spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    // First item (position 0) is "No Category"
                    selectedCategoryId = if (position == 0) {
                        null
                    } else if (position <= categories.size) { // Ensure position is valid
                        // Adjust index for the actual categories list
                        categories[position - 1].id
                    } else {
                        // Handle case when position is out of bounds
                        null
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    selectedCategoryId = null
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if user is still authenticated
        if (FirebaseAuth.getInstance().currentUser == null) {
            Toast.makeText(this, "Session expired, please login again", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure any pending sync operations are cleaned up
        val db = AppDatabase.getInstance(this)
        val dataSync = FirebaseDataSync.getInstance(db)
        try {
            dataSync.cleanup()
        } catch (e: Exception) {
            // Prevent crash if cleanup method is not properly implemented
        }
    }

    private fun updateSelectedDateDisplay(timeInMillis: Long) {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        tvSelectedDate.text = sdf.format(Date(timeInMillis))
    }

    /**
     * Compresses an image to a smaller size (max 800x800 pixels)
     */
    private fun compressImage(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        val maxWidth = 800
        val maxHeight = 800
        val width = bitmap.width
        val height = bitmap.height

        // Calculate new dimensions while maintaining aspect ratio
        var newWidth = width
        var newHeight = height

        if (width > height && width > maxWidth) {
            newWidth = maxWidth
            newHeight = (height * (maxWidth.toFloat() / width)).toInt()
        } else if (height > width && height > maxHeight) {
            newHeight = maxHeight
            newWidth = (width * (maxHeight.toFloat() / height)).toInt()
        } else if (width > maxWidth && height > maxHeight) {
            newWidth = maxWidth
            newHeight = maxHeight
        }

        return android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun openImageChooser() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when {
            requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data?.data != null -> {
                selectedImageUri = data.data
                findViewById<ImageView>(R.id.ivPhoto).setImageURI(selectedImageUri)
            }
            requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK -> {
                selectedImageUri = cameraImageUri
                findViewById<ImageView>(R.id.ivPhoto).setImageURI(selectedImageUri)
            }
        }
    }




}


