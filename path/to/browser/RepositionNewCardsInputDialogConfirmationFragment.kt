# Reposition new cards input dialog confirmation fragment
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.DialogUtils

class RepositionNewCardsInputDialogConfirmationFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.reposition_new_cards_input_dialog_confirmation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val input = arguments?.getString("input")
        view.findViewById<TextView>(R.id.input).text = input
        view.findViewById<Button>(R.id.ok).setOnClickListener {
            val fragmentManager: FragmentManager = requireFragmentManager()
            val fragment: Fragment = RepositionNewCardsDialogFragment()
            fragment.show(fragmentManager, "reposition_new_cards")
        }
    }
}