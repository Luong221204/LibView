package com.hyzangly.otp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.InputFilter
import android.text.InputType
import android.util.AttributeSet
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.withStyledAttributes

class OptEditView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle,
    defStyleRes: Int = 0,
): AppCompatEditText(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val strokeWidthPx = 2f * density
    private var optLength = 6
    private var distanceBetweenOptCell = 8f * density // 8dp
    private var optCellWidth = 24f * density          // 24dp
    private var emptyCellColor = Color.LTGRAY
    private var focusedCellColor = Color.parseColor("#2196F3")
    private var isMarked = true
    private var markPointColor = Color.RED
    private var shape = "rectangle"
    private var maskDotRadius = 4f * density
    private var visibleCharIndex = -1
    private val maskRunnable = Runnable {
        visibleCharIndex = -1
        invalidate() // Yêu cầu vẽ lại view
    }
    private val mCellPaintEmpty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        color = Color.LTGRAY
    }
    private val mCellTextFilled = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        color = Color.RED
    }
    private val mCellPaintFocused = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx + 1f * density
        color = Color.parseColor("#2196F3")
    }
    private val mTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK                         // Màu chữ số
        textSize = 18f * resources.displayMetrics.scaledDensity // Cỡ chữ (dùng sp)
        textAlign = Paint.Align.CENTER              // Tự động căn giữa theo trục X
        isFakeBoldText = true                       // Đậm chữ một chút cho rõ
    }
    private val mMaskDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK                         // Màu chữ số
        style = Paint.Style.FILL
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 1. Tính toán chiều rộng mong muốn dựa trên số ô và khoảng cách giữa các ô
        val totalCellsWidth = optLength * optCellWidth
        val totalSpacesWidth = if (optLength > 1) (optLength - 1) * distanceBetweenOptCell else 0f
        val desiredWidth = (paddingLeft + totalCellsWidth + totalSpacesWidth + paddingRight).toInt()

        // 2. Tính toán chiều cao mong muốn (chiều cao của ô + padding trên/dưới)
        val desiredHeight = (paddingTop + optCellWidth + paddingBottom).toInt()

        // 3. Dùng resolveSize để tôn trọng các ràng buộc từ layout cha (match_parent, kích thước cố định hoặc wrap_content)
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)

        // 4. Báo lại kích thước cuối cùng cho hệ thống Android
        setMeasuredDimension(width, height)
    }
    init {
        context.withStyledAttributes(attrs, R.styleable.OptEditView, defStyleAttr, 0) {
            optLength = getInteger(R.styleable.OptEditView_optLength, optLength)
            optCellWidth = getDimension(R.styleable.OptEditView_optCellWidth, optCellWidth)
            distanceBetweenOptCell = getDimension(R.styleable.OptEditView_distanceBetweenOptCell, distanceBetweenOptCell)
            emptyCellColor = getColor(R.styleable.OptEditView_emptyCellColor, emptyCellColor)
            focusedCellColor = getColor(R.styleable.OptEditView_focusedCellColor, focusedCellColor)
        }

        // Cập nhật màu lại cho Paint sau khi đã đọc thuộc tính XML
        mCellPaintEmpty.color = emptyCellColor
        mCellPaintFocused.color = focusedCellColor
        background = null
        isCursorVisible = false
        setTextColor(Color.TRANSPARENT)
        isFocusable = true
        isFocusableInTouchMode = true
        inputType = InputType.TYPE_CLASS_NUMBER

        // Custom filter: Chặn vượt quá 6 ký tự, nếu cố gõ thêm thì thay thế ký tự cuối
        filters = arrayOf(InputFilter { source, start, end, dest, dstart, dend ->
            // source: Ký tự mới được gõ vào (từ bàn phím)
            // dest: Chuỗi hiện có trong EditText

            val newTextLength = source.length - (end - start)
            val resultingLength = dest.length - (dend - dstart) + (end - start)

            // Chỉ can thiệp khi người dùng đang nhập thêm ký tự (không phải phím xóa)
            if (source.isNotEmpty()) {
                // Trường hợp 1: Đã đủ 6 ký tự và gõ thêm
                if (dest.length >= optLength && dstart >= optLength) {
                    // Lấy ký tự mới nhất vừa bấm
                    val charToReplace = source.subSequence(start, end).last().toString()

                    // Thay thế ký tự cuối cùng của chuỗi hiện tại
                    post {
                        val updatedText = dest.substring(0, optLength - 1) + charToReplace
                        setText(updatedText)
                        setSelection(updatedText.length) // Đưa con trỏ về cuối
                    }
                    return@InputFilter "" // Hủy hành động chèn mặc định để tránh vượt quá 6
                }

                if (resultingLength > optLength) {
                    val keep = optLength - (dest.length - (dend - dstart))
                    return@InputFilter if (keep > 0) source.subSequence(start, start + keep) else ""
                }
            }

            null
        })

        setOnClickListener {
            requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }
    var isMasked: Boolean = false
    private val mCellRect = RectF()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val startX = paddingLeft.toFloat()
        val startY = paddingTop.toFloat()
        val currentText = text?.toString() ?: ""
        val currentFocusIndex = currentText.length

        // Tính toán độ lệch Y cho chữ để nằm chính xác giữa ô
        val fontMetrics = mTextPaint.fontMetrics
        val textYOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f

        for (i in 0 until optLength) {
            val left = startX + i * (optCellWidth + distanceBetweenOptCell)
            val right = left + optCellWidth
            val bottom = startY + optCellWidth

            mCellRect.set(left, startY, right, bottom)

            val paintToUse = getCellPaintToUse(currentFocusIndex,i)
            canvas.drawRoundRect(mCellRect, 8f * density, 8f * density, paintToUse)
            if (i < currentText.length) {
                val cx = mCellRect.centerX()
                val cy = mCellRect.centerY()

                if (isMasked) {
                    // Nếu là ký tự vừa gõ và chưa hết 1 giây -> Hiện số
                    if (i == visibleCharIndex) {
                        canvas.drawText(currentText[i].toString(), cx, cy - textYOffset, mCellTextFilled)
                    } else {
                        // Còn lại -> Vẽ dấu chấm tròn
                        canvas.drawCircle(cx, cy, maskDotRadius, mMaskDotPaint)
                    }
                } else {
                    // Chế độ không ẩn mật khẩu -> Luôn vẽ số
                    canvas.drawText(currentText[i].toString(), cx, cy - textYOffset, mCellTextFilled)
                }
            }
        }
    }

    private fun getCellPaintToUse(currentIndexPosition:Int, numberInSequence: Int): Paint{
        return if (isFocused && numberInSequence == currentIndexPosition) {
            mCellPaintFocused
        } else if(numberInSequence < currentIndexPosition){
            mCellTextFilled
        }else{
            mCellPaintEmpty
        }
    }
    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        invalidate()
    }

    // Vẽ lại khi gõ chữ hoặc xóa chữ để ô focus nhảy sang vị trí kế tiếp
    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        if (lengthAfter > lengthBefore && !text.isNullOrEmpty()) {
            visibleCharIndex = text.length - 1 // Ô vừa gõ là ô cuối cùng

            // Hủy lịch hẹn cũ (nếu người dùng gõ nhanh nhiều số liên tiếp)
            removeCallbacks(maskRunnable)
            // Hẹn 1 giây sau sẽ che thành dấu chấm
            postDelayed(maskRunnable, 1000)
        } else {
            // Nếu xóa ký tự thì không cần hiển thị tạm
            visibleCharIndex = -1
            removeCallbacks(maskRunnable)
        }

        invalidate()

    }
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Tránh rò rỉ bộ nhớ (Memory Leak) khi View bị hủy
        removeCallbacks(maskRunnable)
    }
}