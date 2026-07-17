# Dialog management utility functions
import android.app.AlertDialog
import android.content.DialogInterface
import android.view.View
import androidx.fragment.app.Fragment

fun Fragment.showDialog(
    title: String,
    message: String,
    positiveButtonText: String,
    negativeButtonText: String = "",
    onPositiveClick: DialogInterface.OnClickListener? = null,
    onNegativeClick: DialogInterface.OnClickListener? = null
) {
    val builder = AlertDialog.Builder(requireContext())
    builder.setTitle(title)
    builder.setMessage(message)
    builder.setPositiveButton(positiveButtonText, onPositiveClick)
    if (negativeButtonText.isNotEmpty()) {
        builder.setNegativeButton(negativeButtonText, onNegativeClick)
    }
    builder.show()
}

fun Fragment.showInputDialog(
    title: String,
    message: String,
    inputHint: String,
    onInputClick: (String) -> Unit
) {
    val builder = AlertDialog.Builder(requireContext())
    builder.setTitle(title)
    builder.setMessage(message)
    val inputView = View.inflate(requireContext(), R.layout.dialog_input, null)
    builder.setView(inputView)
    builder.setPositiveButton("OK") { _, _ ->
        val input = inputView.findViewById<EditText>(R.id.input).text.toString()
        onInputClick(input)
    }
    builder.show()
}