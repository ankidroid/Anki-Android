# Reposition new cards dialog fragment
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.DialogUtils

class RepositionNewCardsDialogFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.reposition_new_cards_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.ok).setOnClickListener {
            val fragmentManager: FragmentManager = requireFragmentManager()
            val fragment: Fragment = RepositionNewCardsInputDialogFragment()
            fragment.show(fragmentManager, "reposition_new_cards_input")
        }
    }
}