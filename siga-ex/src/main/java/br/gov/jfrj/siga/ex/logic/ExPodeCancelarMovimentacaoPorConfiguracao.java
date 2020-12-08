package br.gov.jfrj.siga.ex.logic;

import com.crivano.jlogic.Expression;
import com.crivano.jlogic.JLogic;

import br.gov.jfrj.siga.cp.CpTipoConfiguracao;
import br.gov.jfrj.siga.dp.DpLotacao;
import br.gov.jfrj.siga.dp.DpPessoa;
import br.gov.jfrj.siga.ex.bl.Ex;

public class ExPodeCancelarMovimentacaoPorConfiguracao implements Expression {

	private long idTpMov;
	private DpPessoa titular;
	private DpLotacao lotaTitular;

	public ExPodeCancelarMovimentacaoPorConfiguracao(long idTpMov, DpPessoa titular, DpLotacao lotaTitular) {
		this.idTpMov = idTpMov;
		this.titular = titular;
		this.lotaTitular = lotaTitular;
	}

	@Override
	public boolean eval() {
		return Ex.getInstance().getConf().podePorConfiguracao(titular, lotaTitular, idTpMov,
				CpTipoConfiguracao.TIPO_CONFIG_MOVIMENTAR);
	}

	@Override
	public String explain(boolean result) {
		return JLogic.explain("pode por configuração", result);
	}
};
