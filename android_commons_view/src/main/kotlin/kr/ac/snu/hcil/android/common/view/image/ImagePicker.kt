package kr.ac.snu.hcil.android.common.view.image

import android.animation.LayoutTransition
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.component_image_picker.view.*
import kr.ac.snu.hcil.android.common.events.Event
import kr.ac.snu.hcil.android.common.view.R
import kr.ac.snu.hcil.android.common.view.applyTint
import kr.ac.snu.hcil.android.common.view.dialog.CameraPickDialogFragment
import kr.ac.snu.hcil.android.common.view.inflateContent
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by younghokim on 16. 9. 6..
 */
class ImagePicker : ConstraintLayout, View.OnClickListener {

    //https://developer.android.com/training/camera/photobasics.html

    interface ImagePickerCallback {
        fun onRequestCameraImage(view: ImagePicker)
        fun onRequestGalleryImage(view: ImagePicker)
    }

    var callback: ImagePickerCallback? = null

    var imageUri: Uri = Uri.EMPTY
        set(value) {
            println("new uri _ $value")
            if (field != value) {
                field = value

                if (value == Uri.EMPTY) {
                    ui_button_cancel.visibility = View.INVISIBLE
                    ui_button_gallery.visibility = View.VISIBLE
                    ui_button_camera.visibility = View.VISIBLE
                    ui_image_view.visibility = View.GONE
                } else {
                    ui_button_cancel.visibility = View.VISIBLE
                    ui_button_gallery.visibility = View.INVISIBLE
                    ui_button_camera.visibility = View.INVISIBLE
                    ui_image_view.visibility = View.VISIBLE
                    Picasso.get().load(value).into(ui_image_view)
                }
                uriChanged.invoke(this, value)
            }
        }

    val uriChanged = Event<Uri>()

    var overrideLocalUriFolderPath: File? = null

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        inflateContent(R.layout.component_image_picker, true)

        layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
        }

        this.background = applyTint(ContextCompat.getDrawable(context, R.drawable.hatching_repeated_wide_gray)!!, Color.parseColor("#e0e0e0"))

        ui_button_camera.setOnClickListener(this)
        ui_button_gallery.setOnClickListener(this)
        ui_button_cancel.setOnClickListener(this)
    }

    override fun onClick(view: View?) {
        if (view === ui_button_camera) {
            callback?.onRequestCameraImage(this)

        } else if (view === ui_button_gallery) {
            callback?.onRequestGalleryImage(this)
        } else if (view === ui_button_cancel) {
            imageUri = Uri.EMPTY
        }
    }


    fun createCacheImageFile(context: Context): File {
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir = this.overrideLocalUriFolderPath ?: context.externalCacheDir
        println("store file to $storageDir")

        val image = File.createTempFile(
                imageFileName, /* prefix */
                ".jpg", /* suffix */
                storageDir      /* directory */)

        println("camera output : ${image.absolutePath}")

        // Save a file: path for use with ACTION_VIEW intents
        return image
    }

    fun dispatchCameraIntent(activity: AppCompatActivity, requestCode: Int): Uri? {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        //ensure there is a camera activity
        if (takePictureIntent.resolveActivity(activity.packageManager) != null) {

            val uri = createCacheImageFileUri(activity)
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)

            activity.startActivityForResult(takePictureIntent, requestCode)

            return uri
        } else return null
    }

    fun dispatchImagePickIntent(activity: AppCompatActivity, requestCode: Int) {
        val getIntent = Intent(Intent.ACTION_GET_CONTENT)
        getIntent.type = "image/*"

        val pickIntent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickIntent.type = "image/*"

        val chooserIntent = Intent.createChooser(getIntent, "Select Image")
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(pickIntent))

        activity.startActivityForResult(chooserIntent, requestCode)
    }

    fun showCameraPickDialog(activity: AppCompatActivity, requestKey: String, maxArea: Int?) {
        val dialog = CameraPickDialogFragment.getInstance(requestKey, maxArea)
        dialog.show(activity.supportFragmentManager, "CAMERA")
    }


    fun createCacheImageFileUri(context: Context): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", createCacheImageFile(context))
    }

}