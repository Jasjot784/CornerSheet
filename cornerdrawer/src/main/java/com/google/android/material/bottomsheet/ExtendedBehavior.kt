package com.google.android.material.bottomsheet

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.annotation.IntDef
import androidx.annotation.StyleableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.github.heyalex.R
import com.github.heyalex.lerp
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

open class ExtendedBehavior<V : View> : BottomSheetBehavior<V> {

    private var horizontalPeekWidth: Int = 0
    private var expandedWidth: Int = 0
    private var currentWidth: Int = 0
    private var horizontalState: Int = STATE_EXPANDED

    private var expandingRatio: Float = 0.2f
    private var isViewRefInitialized: Boolean = false
    private var sheetBackground: MaterialShapeDrawable? = null

    constructor() : super()
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        val typedArray =
            context.obtainStyledAttributes(attrs, R.styleable.CornerSheetBehavior_Layout)
        horizontalPeekWidth = typedArray.getDimensionPixelSize(
            R.styleable.CornerSheetBehavior_Layout_behavior_horizontal_peekHeight,
            -1
        )

        expandingRatio = typedArray.getFloat(
            R.styleable.CornerSheetBehavior_Layout_behavior_horizontalExpandingRatio,
            0.2f
        )

        expandedWidth = typedArray.getDimensionPixelSize(
            R.styleable.CornerSheetBehavior_Layout_behavior_expanded_width,
            0
        )

        sheetBackground = MaterialShapeDrawable(
            ShapeAppearanceModel.builder(
                context,
                attrs,
                R.attr.bottomSheetStyle,
                0
            ).build()
        )

        val typedArrayBottomSheet =
            context.obtainStyledAttributes(attrs, R.styleable.BottomSheetBehavior_Layout)

        val bottomSheetColor =
            getColorStateList(
                context,
                typedArrayBottomSheet,
                R.styleable.BottomSheetBehavior_Layout_backgroundTint
            )

        typedArrayBottomSheet.recycle()
        if (bottomSheetColor != null) {
            sheetBackground?.fillColor = bottomSheetColor
        } else {
            val defaultColor = TypedValue()
            context.theme.resolveAttribute(android.R.attr.colorBackground, defaultColor, true)
            sheetBackground?.setTint(defaultColor.data)
        }

        typedArray.recycle()

        currentWidth = getMaxWidth()
    }

    fun setHorizontalPeekHeight(width: Int, animate: Boolean) {
        getView {
            horizontalPeekWidth = width

            if (state == BottomSheetBehavior.STATE_COLLAPSED || horizontalState != STATE_HIDDEN) {
                if (animate) {
                    setHorizontalState(STATE_COLLAPSED)
                } else {
                    expandedWidth = width
                    it.translationX = it.width - width.toFloat()
                }
            }
        }
    }

    fun getView(unit: ((V) -> Unit)) {
        if (viewRef != null && viewRef!!.get() != null) {
            unit.invoke(viewRef!!.get()!!)
        }
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        val onLayoutChildResult = super.onLayoutChild(parent, child, layoutDirection)
        if (!isViewRefInitialized) {
            ViewCompat.setBackground(child, sheetBackground)

            val invertExpandedValue = child.width - currentWidth.toFloat()
            if (state == BottomSheetBehavior.STATE_EXPANDED) {
                child.translationX = 0f
                sheetBackground?.interpolation = 0f
            } else {
                child.translationX = invertExpandedValue
                sheetBackground?.interpolation = 1f
            }

            addBottomSheetCallback(object :
                BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    val translationValue = child.width - getMaxWidth()
                    child.translationX =
                        lerp(translationValue.toFloat(), 0f, 0f, expandingRatio, slideOffset)
                    sheetBackground?.interpolation = lerp(1f, 0f, 0f, expandingRatio, slideOffset)
                }

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                }
            })

            isViewRefInitialized = true
        }

        return onLayoutChildResult
    }

    override fun onTouchEvent(parent: CoordinatorLayout, child: V, event: MotionEvent): Boolean {
        return if (event.x < child.translationX) {
            if (state == STATE_DRAGGING) {
                state = BottomSheetBehavior.STATE_COLLAPSED
            }
            false
        } else {
            super.onTouchEvent(parent, child, event)
        }
    }

    fun setHorizontalState(@HorizontalState state: Int) {
        getView {
            if (horizontalState == state) return@getView
            horizontalState = state
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 150
                val start = currentWidth
                val end = getMaxWidth()

                addUpdateListener { animation ->
                    val value = animation.animatedValue as Float
                    val lerp = lerp(start.toFloat(), end.toFloat(), 0f, 1f, value)
                    it.translationX = lerp
                    android.util.Log.d(
                        "animation_value",
                        value.toString() + " with translationY: " + lerp
                    )
                    android.util.Log.d("animation_value", "Start: " + start + "  end: " + end)
                }

                currentWidth = end
            }.start()
        }
    }

    override fun onAttachedToLayoutParams(layoutParams: CoordinatorLayout.LayoutParams) {
        super.onAttachedToLayoutParams(layoutParams)
        isViewRefInitialized = false
    }

    override fun onDetachedFromLayoutParams() {
        super.onDetachedFromLayoutParams()
        isViewRefInitialized = false
    }

    private fun getMaxWidth(): Int {
        return when (horizontalState) {
            STATE_EXPANDED -> expandedWidth
            STATE_COLLAPSED -> horizontalPeekWidth
            STATE_HIDDEN -> 0
            else -> return 0
        }
    }

    @IntDef(
        STATE_EXPANDED,
        STATE_COLLAPSED,
        STATE_HIDDEN
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class HorizontalState

    companion object {
        const val STATE_EXPANDED = 3
        const val STATE_COLLAPSED = 4
        const val STATE_HIDDEN = 5

        fun getColorStateList(
            context: Context, attributes: TypedArray, @StyleableRes index: Int
        ): ColorStateList? {
            if (attributes.hasValue(index)) {
                val resourceId = attributes.getResourceId(index, 0)
                if (resourceId != 0) {
                    val value =
                        AppCompatResources.getColorStateList(context, resourceId)
                    if (value != null) {
                        return value
                    }
                }
            }
            return attributes.getColorStateList(index)
        }
    }
}