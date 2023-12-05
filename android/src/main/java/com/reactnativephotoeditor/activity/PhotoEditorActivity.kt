package com.reactnativephotoeditor.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnticipateOvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.reactnativephotoeditor.R
import com.reactnativephotoeditor.activity.StickerFragment.StickerListener
import com.reactnativephotoeditor.activity.constant.ResponseCode
import com.reactnativephotoeditor.activity.filters.FilterListener
import com.reactnativephotoeditor.activity.filters.FilterViewAdapter
import com.reactnativephotoeditor.activity.tools.EditingToolsAdapter
import com.reactnativephotoeditor.activity.tools.EditingToolsAdapter.OnItemSelected
import com.reactnativephotoeditor.activity.tools.ToolType
import ja.burhanrashid52.photoeditor.*
import ja.burhanrashid52.photoeditor.PhotoEditor.OnSaveListener
import ja.burhanrashid52.photoeditor.shape.ShapeBuilder
import ja.burhanrashid52.photoeditor.shape.ShapeType
import java.io.File


open class PhotoEditorActivity : AppCompatActivity(), OnPhotoEditorListener, View.OnClickListener,
  PropertiesBSFragment.Properties, ShapeBSFragment.Properties, StickerListener,
  OnItemSelected, FilterListener {
  private var mPhotoEditor: PhotoEditor? = null
  private var mProgressDialog: ProgressDialog? = null
  private var mPhotoEditorView: PhotoEditorView? = null
  private var mPropertiesBSFragment: PropertiesBSFragment? = null
  private var mShapeBSFragment: ShapeBSFragment? = null
  private var mShapeBuilder: ShapeBuilder? = null
  private var mStickerFragment: StickerFragment? = null
  private var mTxtCurrentTool: TextView? = null
  private var mRvTools: RecyclerView? = null
  private var mRvFilters: RecyclerView? = null
  private val mEditingToolsAdapter = EditingToolsAdapter(this)
  private val mFilterViewAdapter = FilterViewAdapter(this)
  private var mRootView: ConstraintLayout? = null
  private val mConstraintSet = ConstraintSet()
  private var mIsFilterVisible = false

  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    makeFullScreen()
    setContentView(R.layout.photo_editor_view)
    initViews()

    //intern
    val value = intent.extras
    val path = value?.getString("path")
    val stickers =
      value?.getStringArrayList("stickers")?.plus(
        assets.list("Stickers")!!
          .map { item -> "/android_asset/Stickers/$item" }) as ArrayList<String>
//    println("stickers: $stickers ${stickers.size}")
//    for (stick in stickers) {
//      print("stick: $stickers")
//    }

    mPropertiesBSFragment = PropertiesBSFragment()
    mPropertiesBSFragment!!.setPropertiesChangeListener(this)

    mStickerFragment = StickerFragment()
    mStickerFragment!!.setStickerListener(this)

//    val stream: InputStream = assets.open("image.png")
//    val d = Drawable.createFromStream(stream, null)
    mStickerFragment!!.setData(stickers)

    mShapeBSFragment = ShapeBSFragment()
    mShapeBSFragment!!.setPropertiesChangeListener(this)

    val llmTools = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    mRvTools!!.layoutManager = llmTools
    mRvTools!!.adapter = mEditingToolsAdapter

    val llmFilters = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    mRvFilters!!.layoutManager = llmFilters
    mRvFilters!!.adapter = mFilterViewAdapter

    val pinchTextScalable = intent.getBooleanExtra(PINCH_TEXT_SCALABLE_INTENT_KEY, true)

    mPhotoEditor = PhotoEditor.Builder(this, mPhotoEditorView)
      .setPinchTextScalable(pinchTextScalable) // set flag to make text scalable when pinch
      .build() // build photo editor sdk
    mPhotoEditor?.setOnPhotoEditorListener(this)
    onToolSelected(ToolType.SHAPE)
//    val drawable = Drawable.cre

    Glide
      .with(this)
      .load(path)
      .listener(object : RequestListener<Drawable> {
        override fun onLoadFailed(
          e: GlideException?,
          model: Any?,
          target: Target<Drawable>?,
          isFirstResource: Boolean
        ): Boolean {
          val intent = Intent()
          intent.putExtra("path", path)
          setResult(ResponseCode.LOAD_IMAGE_FAILED, intent)
          return false
        }

        override fun onResourceReady(
          resource: Drawable?,
          model: Any?,
          target: Target<Drawable>?,
          dataSource: DataSource?,
          isFirstResource: Boolean
        ): Boolean {
          //
          return false
        }
      })
//      .placeholder(drawable)
      .into(mPhotoEditorView!!.source);
  }

  private fun showLoading(message: String) {
    mProgressDialog = ProgressDialog(this)
    mProgressDialog!!.setMessage(message)
    mProgressDialog!!.setProgressStyle(ProgressDialog.STYLE_SPINNER)
    mProgressDialog!!.setCancelable(false)
    mProgressDialog!!.show()
  }

  protected fun hideLoading() {
    if (mProgressDialog != null) {
      mProgressDialog!!.dismiss()
    }
  }

  private fun requestPermission(permission: String) {
    val isGranted =
      ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    if (!isGranted) {
      ActivityCompat.requestPermissions(
        this, arrayOf(permission),
        READ_WRITE_STORAGE
      )
    }
  }

  private fun makeFullScreen() {
    requestWindowFeature(Window.FEATURE_NO_TITLE)
    window.setFlags(
      WindowManager.LayoutParams.FLAG_FULLSCREEN,
      WindowManager.LayoutParams.FLAG_FULLSCREEN
    )
  }

  private fun initViews() {
    //REDO
    val imgRedo: ImageView = findViewById(R.id.imgRedo)
    imgRedo.setOnClickListener(this)
    //UNDO
    val imgUndo: ImageView = findViewById(R.id.imgUndo)
    imgUndo.setOnClickListener(this)
    //CLOSE
    val imgClose: ImageView = findViewById(R.id.imgClose)
    imgClose.setOnClickListener(this)
    //SAVE
    val btnSave: TextView = findViewById(R.id.btnSave)
    btnSave.setOnClickListener(this)
    btnSave.setTextColor(Color.BLACK)

    mPhotoEditorView = findViewById(R.id.photoEditorView)
    mTxtCurrentTool = findViewById(R.id.txtCurrentTool)
    mRvTools = findViewById(R.id.rvConstraintTools)
    mRvFilters = findViewById(R.id.rvFilterView)
    mRootView = findViewById(R.id.rootView)
  }

  override fun onEditTextChangeListener(rootView: View, text: String, colorCode: Int) {
    val textEditorDialogFragment = TextEditorDialogFragment.show(this, text, colorCode)
    textEditorDialogFragment.setOnTextEditorListener { inputText: String?, newColorCode: Int ->
      val styleBuilder = TextStyleBuilder()
      styleBuilder.withTextColor(newColorCode)
      mPhotoEditor!!.editText(rootView, inputText, styleBuilder)
      mTxtCurrentTool!!.setText(R.string.label_text)
    }
  }

  override fun onAddViewListener(viewType: ViewType, numberOfAddedViews: Int) {
    Log.d(
      TAG,
      "onAddViewListener() called with: viewType = [$viewType], numberOfAddedViews = [$numberOfAddedViews]"
    )
  }

  override fun onRemoveViewListener(viewType: ViewType, numberOfAddedViews: Int) {
    Log.d(
      TAG,
      "onRemoveViewListener() called with: viewType = [$viewType], numberOfAddedViews = [$numberOfAddedViews]"
    )
  }

  override fun onStartViewChangeListener(viewType: ViewType) {
    Log.d(TAG, "onStartViewChangeListener() called with: viewType = [$viewType]")
  }

  override fun onStopViewChangeListener(viewType: ViewType) {
    Log.d(TAG, "onStopViewChangeListener() called with: viewType = [$viewType]")
  }

  @SuppressLint("NonConstantResourceId")
  override fun onClick(view: View) {
    when (view.id) {
      R.id.imgUndo -> {
        mPhotoEditor!!.undo()
      }
      R.id.imgRedo -> {
        mPhotoEditor!!.redo()
      }
      R.id.btnSave -> {
        saveImage()
      }
      R.id.imgClose -> {
        onBackPressed()
      }
    }
  }

  private fun isSdkHigherThan28(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
  }

  private fun saveImage() {
    val fileName = System.currentTimeMillis().toString() + ".png"
    val hasStoragePermission = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
    if (hasStoragePermission || isSdkHigherThan28()) {
      showLoading("保存中...")
      val path: File = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_PICTURES
      )
      val file = File(path, fileName)
      path.mkdirs()

      mPhotoEditor!!.saveAsFile(file.absolutePath, object : OnSaveListener {
        override fun onSuccess(@NonNull imagePath: String) {
          hideLoading()
          val intent = Intent()
          intent.putExtra("path", imagePath)
          setResult(ResponseCode.RESULT_OK, intent)
          finish()
        }

        override fun onFailure(@NonNull exception: Exception) {
          hideLoading()
          if (!hasStoragePermission) {
            requestPer()
          } else {
            mPhotoEditorView?.let {
              val snackBar = Snackbar.make(
                it, R.string.save_error,
                Snackbar.LENGTH_SHORT)
              snackBar.setBackgroundTint(Color.WHITE)
              snackBar.setActionTextColor(Color.BLACK)
              snackBar.setAction("Ok", null).show()
            }
          }
        }
      })
    } else {
      requestPer()
    }
  }

  private fun requestPer() {
    requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
  }

  override fun onColorChanged(colorCode: Int) {
    mPhotoEditor!!.setShape(mShapeBuilder!!.withShapeColor(colorCode))
    mTxtCurrentTool!!.setText(R.string.label_brush)
  }

  override fun onOpacityChanged(opacity: Int) {
    mPhotoEditor!!.setShape(mShapeBuilder!!.withShapeOpacity(opacity))
    mTxtCurrentTool!!.setText(R.string.label_brush)
  }

  override fun onShapeSizeChanged(shapeSize: Int) {
    mPhotoEditor!!.setShape(mShapeBuilder!!.withShapeSize(shapeSize.toFloat()))
    mTxtCurrentTool!!.setText(R.string.label_brush)
  }

  override fun onShapePicked(shapeType: ShapeType) {
    mPhotoEditor!!.setShape(mShapeBuilder!!.withShapeType(shapeType))
  }

  override fun onStickerClick(bitmap: Bitmap) {
    mPhotoEditor!!.addImage(bitmap)
    mTxtCurrentTool!!.setText(R.string.label_sticker)
  }

  private fun showSaveDialog() {
    val builder = AlertDialog.Builder(this)
    builder.setMessage(getString(R.string.msg_save_image))
    builder.setPositiveButton("保存") { _: DialogInterface?, _: Int -> saveImage() }
    builder.setNegativeButton("取消") { dialog: DialogInterface, _: Int -> dialog.dismiss() }
    builder.setNeutralButton("关闭") { _: DialogInterface?, _: Int -> onCancel() }
    builder.create().show()
  }

  private fun onCancel() {
    val intent = Intent()
    setResult(ResponseCode.RESULT_CANCELED, intent)
    finish()
  }

  override fun onFilterSelected(photoFilter: PhotoFilter) {
    mPhotoEditor!!.setFilterEffect(photoFilter)
  }

  override fun onToolSelected(toolType: ToolType) {
    when (toolType) {
      ToolType.SHAPE -> {
        mPhotoEditor!!.setBrushDrawingMode(true)
        mShapeBuilder = ShapeBuilder()
        mPhotoEditor!!.setShape(mShapeBuilder)
        mTxtCurrentTool!!.setText(R.string.label_shape)
        mPhotoEditor!!.setShape(mShapeBuilder!!.withShapeSize(5.0f))
        // 关闭形状设置弹窗
        // showBottomSheetDialogFragment(mShapeBSFragment)
      }
      ToolType.TEXT -> {
        val textEditorDialogFragment = TextEditorDialogFragment.show(this)
        textEditorDialogFragment.setOnTextEditorListener { inputText: String?, colorCode: Int ->
          val styleBuilder = TextStyleBuilder()
          styleBuilder.withTextColor(colorCode)
          mPhotoEditor!!.addText(inputText, styleBuilder)
          mTxtCurrentTool!!.setText(R.string.label_text)
        }
      }
      ToolType.ERASER -> {
        mPhotoEditor!!.brushEraser()
        mTxtCurrentTool!!.setText(R.string.label_eraser_mode)
      }
      // ToolType.FILTER -> {
      //   mTxtCurrentTool!!.setText(R.string.label_filter)
      //   showFilter(true)
      // }
      // ToolType.STICKER -> showBottomSheetDialogFragment(mStickerFragment)
    }
  }

  private fun showBottomSheetDialogFragment(fragment: BottomSheetDialogFragment?) {
    if (fragment == null || fragment.isAdded) {
      return
    }
    fragment.show(supportFragmentManager, fragment.tag)
  }

  fun showFilter(isVisible: Boolean) {
    mIsFilterVisible = isVisible
    mConstraintSet.clone(mRootView)
    if (isVisible) {
      mConstraintSet.clear(mRvFilters!!.id, ConstraintSet.START)
      mConstraintSet.connect(
        mRvFilters!!.id, ConstraintSet.START,
        ConstraintSet.PARENT_ID, ConstraintSet.START
      )
      mConstraintSet.connect(
        mRvFilters!!.id, ConstraintSet.END,
        ConstraintSet.PARENT_ID, ConstraintSet.END
      )
    } else {
      mConstraintSet.connect(
        mRvFilters!!.id, ConstraintSet.START,
        ConstraintSet.PARENT_ID, ConstraintSet.END
      )
      mConstraintSet.clear(mRvFilters!!.id, ConstraintSet.END)
    }
    val changeBounds = ChangeBounds()
    changeBounds.duration = 350
    changeBounds.interpolator = AnticipateOvershootInterpolator(1.0f)
    TransitionManager.beginDelayedTransition(mRootView!!, changeBounds)
    mConstraintSet.applyTo(mRootView)
  }

  override fun onBackPressed() {
    if (mIsFilterVisible) {
      showFilter(false)
      mTxtCurrentTool!!.setText(R.string.app_name)
    } else if (!mPhotoEditor!!.isCacheEmpty) {
      showSaveDialog()
    } else {
      onCancel()
    }
  }

  companion object {
    private val TAG = PhotoEditorActivity::class.java.simpleName
    const val PINCH_TEXT_SCALABLE_INTENT_KEY = "PINCH_TEXT_SCALABLE"
    const val READ_WRITE_STORAGE = 52
  }
}
