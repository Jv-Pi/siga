/*******************************************************************************
 * Copyright (c) 2006 - 2011 SJRJ.
 * 
 *     This file is part of SIGA.
 * 
 *     SIGA is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     SIGA is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with SIGA.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
/*
 * Criado em  13/09/2005
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package br.gov.jfrj.siga.vraptor;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletRequest;

import org.jboss.logging.Logger;

import br.com.caelum.vraptor.Controller;
import br.com.caelum.vraptor.Get;
import br.com.caelum.vraptor.Post;
import br.com.caelum.vraptor.Result;
import br.com.caelum.vraptor.view.Results;
import br.gov.jfrj.siga.base.AplicacaoException;
import br.gov.jfrj.siga.cp.CpMarcadorCorEnum;
import br.gov.jfrj.siga.cp.CpMarcadorIconeEnum;
import br.gov.jfrj.siga.cp.CpMarcadorTipoAplicacaoEnum;
import br.gov.jfrj.siga.cp.CpMarcadorTipoDataEnum;
import br.gov.jfrj.siga.cp.CpTipoMarcadorEnum;
import br.gov.jfrj.siga.cp.CpMarcadorTipoExibicaoEnum;
import br.gov.jfrj.siga.cp.CpMarcadorTipoInteressadoEnum;
import br.gov.jfrj.siga.cp.CpMarcadorTipoTextoEnum;
import br.gov.jfrj.siga.cp.bl.Cp;
import br.gov.jfrj.siga.dp.CpMarcador;
import br.gov.jfrj.siga.dp.CpOrgaoUsuario;
import br.gov.jfrj.siga.dp.DpLotacao;
import br.gov.jfrj.siga.dp.dao.CpDao;
import br.gov.jfrj.siga.dp.dao.DpLotacaoDaoFiltro;
import br.gov.jfrj.siga.model.dao.ModeloDao;

@Controller
public class CpMarcadorController extends SigaController {

	private static final Logger LOG = Logger.getLogger(CpMarcadorController.class);
	private static final String ACESSO_CAD_MARCADOR = "FE: Ferramentas;CAD_MARCADOR: Cadastro de Marcadores;";
	private static final String ACESSO_CAD_MARCADOR_LOTA = "MAR_LOTA:Cadastro de Marcador da Lotação;";
	private static final String ACESSO_CAD_MARCADOR_GERAL = "MAR_GERAL:Cadastro de Marcador Geral;";

	/**
	 * @deprecated CDI eyes only
	 */
	public CpMarcadorController() {
		super();
	}

	@Inject
	public CpMarcadorController(HttpServletRequest request, Result result, CpDao dao, SigaObjects so,
			EntityManager em) {
		super(request, result, dao, so, em);
	}

	@Get("/app/marcador/listar")
	public void lista() throws Exception {
		assertAcesso("");
		result.include("listaTipoMarcador", CpTipoMarcadorEnum.values());
		result.include("listaCores", CpMarcadorCorEnum.values());
		result.include("listaIcones", CpMarcadorIconeEnum.values());
		result.include("listaTipoAplicacao", CpMarcadorTipoAplicacaoEnum.values());
		result.include("listaTipoExibicao", CpMarcadorTipoExibicaoEnum.values());
		result.include("listaTipoDataPlanejada", CpMarcadorTipoDataEnum.values());
		result.include("listaTipoDataLimite", CpMarcadorTipoDataEnum.values());
		result.include("listaTipoTexto", CpMarcadorTipoTextoEnum.values());
		result.include("listaTipoInteressado", CpMarcadorTipoInteressadoEnum.values());
		result.include("listaMarcadores", dao.listarCpMarcadoresPorLotacaoESublotacoes(getLotaCadastrante(), true));
	}

	@Get("/app/marcador/historico")
	public void historico(final Long id) throws Exception {
		assertAcesso("");
		List<CpMarcador> listMar = dao().listarTodosPorIdInicial(CpMarcador.class, id, "hisDtIni", true);
		result.include("listaHistorico", listMar);
	}

	@Transacional
	@Post("/app/marcador/excluir")
	public void exclui(final Long id) throws Exception {
		assertAcesso(ACESSO_CAD_MARCADOR_LOTA);
		try {
			if (id != null) {
				CpMarcador mar = dao().consultar(id, CpMarcador.class, false);
				mar.setHisAtivo(0);
				dao().excluirComHistorico(mar, null, getIdentidadeCadastrante());
				result.use(Results.http()).addHeader("Content-Type", "application/text")
					.setStatusCode(200);
			}
		} catch (AplicacaoException e) {
			result.use(Results.http()).addHeader("Content-Type", "application/text")
					.body("Erro na exclusão do marcador: " + e.getMessage()).setStatusCode(400);
		}
	}

	@Transacional
	@Get("/app/marcador/editar")
	public void edita(final Long id) {
		assertAcesso(ACESSO_CAD_MARCADOR_LOTA);
		
		if (id != null) {
			CpMarcador marcador = dao().consultar(id, CpMarcador.class, false);
			marcador = dao().obterAtual(marcador);
			result.include("marcador", marcador);
		}
		result.include("listaTipoMarcador", listarTipoMarcador());
		result.include("listaCores", CpMarcadorCorEnum.values());
		result.include("listaIcones", CpMarcadorIconeEnum.values());
		result.include("listaTipoAplicacao", CpMarcadorTipoAplicacaoEnum.values());
		result.include("listaTipoExibicao", CpMarcadorTipoExibicaoEnum.values());
		result.include("listaTipoDataPlanejada", CpMarcadorTipoDataEnum.values());
		result.include("listaTipoDataLimite", CpMarcadorTipoDataEnum.values());
		result.include("listaTipoTexto", CpMarcadorTipoTextoEnum.values());
		result.include("listaTipoInteressado", CpMarcadorTipoInteressadoEnum.values());
	}

	@Transacional
	@Post("/app/marcador/gravar")
	public void marcadorGravar(Long id, final String sigla, final String descricao, final String descrDetalhada,
			final CpMarcadorCorEnum idCor, final CpMarcadorIconeEnum idIcone, final Integer grupoId, final CpTipoMarcadorEnum idTpMarcador,
			final CpMarcadorTipoAplicacaoEnum idTpAplicacao, final CpMarcadorTipoDataEnum idTpDataPlanejada, final CpMarcadorTipoDataEnum idTpDataLimite,
			final CpMarcadorTipoExibicaoEnum idTpExibicao, final CpMarcadorTipoTextoEnum idTpTexto, final CpMarcadorTipoInteressadoEnum idTpInteressado)
			throws Exception {

		assertAcesso(ACESSO_CAD_MARCADOR_LOTA);
		
		if (idTpMarcador == CpTipoMarcadorEnum.TIPO_MARCADOR_GERAL)		
			assertAcesso(ACESSO_CAD_MARCADOR_GERAL);

		if (id != null) {
			CpMarcador marcador = dao().consultar(id, CpMarcador.class, false);
			marcador = dao().obterAtual(marcador);
			id = marcador.getId();
		}
		
		Cp.getInstance().getBL().gravarMarcadorDaLotacao(id, getCadastrante(), getLotaTitular(),
				getIdentidadeCadastrante(), descricao, descrDetalhada, idCor, idIcone, 14, idTpMarcador,
				idTpAplicacao, idTpDataPlanejada, idTpDataLimite, idTpExibicao, idTpTexto, idTpInteressado);

		result.redirectTo(this).lista();
	}

	private List<CpTipoMarcadorEnum> listarTipoMarcador() {
		boolean podeMarcadorGeral;
		try {
			assertAcesso(ACESSO_CAD_MARCADOR_GERAL);
			podeMarcadorGeral = true;
		} catch (AplicacaoException e) {
			podeMarcadorGeral = false;
		}

		List<CpTipoMarcadorEnum> lstTpMarcador = new ArrayList<>();
		for (CpTipoMarcadorEnum tipoMar : CpTipoMarcadorEnum.values()) {
			if (tipoMar == CpTipoMarcadorEnum.TIPO_MARCADOR_GERAL
					&& !podeMarcadorGeral)
				continue;
			if (tipoMar == CpTipoMarcadorEnum.TIPO_MARCADOR_SISTEMA)
				continue;
			lstTpMarcador.add(tipoMar);
		};
		return lstTpMarcador;
	}

	protected void assertAcesso(String pathServico) throws AplicacaoException {
		super.assertAcesso(ACESSO_CAD_MARCADOR 
					+ pathServico);
	}
}