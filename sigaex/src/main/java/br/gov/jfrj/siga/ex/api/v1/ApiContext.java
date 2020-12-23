package br.gov.jfrj.siga.ex.api.v1;

import static java.util.Objects.isNull;

import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;

import org.jboss.security.auth.spi.AnonLoginModule;

import com.auth0.jwt.internal.com.fasterxml.jackson.databind.util.Annotations;
import com.crivano.swaggerservlet.SwaggerAuthorizationException;
import com.crivano.swaggerservlet.SwaggerServlet;

import br.gov.jfrj.siga.base.AplicacaoException;
import br.gov.jfrj.siga.base.log.RequestLoggerFilter;
import br.gov.jfrj.siga.dp.DpLotacao;
import br.gov.jfrj.siga.dp.DpPessoa;
import br.gov.jfrj.siga.ex.ExMobil;
import br.gov.jfrj.siga.ex.ExPapel;
import br.gov.jfrj.siga.ex.bl.CurrentRequest;
import br.gov.jfrj.siga.ex.bl.Ex;
import br.gov.jfrj.siga.ex.bl.RequestInfo;
import br.gov.jfrj.siga.hibernate.ExDao;
import br.gov.jfrj.siga.hibernate.ExStarter;
import br.gov.jfrj.siga.model.ContextoPersistencia;
import br.gov.jfrj.siga.model.dao.ModeloDao;
import br.gov.jfrj.siga.vraptor.SigaObjects;

public class ApiContext implements Closeable {
	EntityManager em;
	boolean transacional;
	long inicio = System.currentTimeMillis();
	private static final String DOC_MÓDULO_DE_DOCUMENTOS = "DOC:Módulo de Documentos;";
	
	public ApiContext(boolean transacional, boolean validaUser, Annotation[] annotations) throws SwaggerAuthorizationException {

		try {
			if (!validaAcesso(annotations)) {
				throw new SwaggerAuthorizationException();
			}
			
		} catch (Exception e) {
			throw new SwaggerAuthorizationException();
		}



		

		    

	}

	public ApiContext(boolean transacional, boolean validaUser) throws SwaggerAuthorizationException {

		this.transacional = transacional;
		em = ExStarter.emf.createEntityManager();
		ContextoPersistencia.setEntityManager(em);
		
		if (validaUser) {
			buscarEValidarUsuarioLogado();
		}

		ModeloDao.freeInstance();
		ExDao.getInstance();
		try {
			Ex.getInstance().getConf().limparCacheSeNecessario();
		} catch (Exception e1) {
			throw new RuntimeException("Não foi possível atualizar o cache de configurações", e1);
		}
		if (this.transacional)
			em.getTransaction().begin();
	}

	public void rollback(Exception e) {
		if (em.getTransaction().isActive())
			em.getTransaction().rollback();
		if (!RequestLoggerFilter.isAplicacaoException(e)) {
			RequestLoggerFilter.logException(null, inicio, e);
		}
	}

	@Override
	public void close() throws IOException {
		try {
			if (this.transacional)
				em.getTransaction().commit();
		} catch (Exception e) {
			if (em.getTransaction().isActive())
				em.getTransaction().rollback();
			throw new RuntimeException(e);
		} finally {
			em.close();
			ContextoPersistencia.setEntityManager(null);
		}
	}

	/**
	 * Retorna uma instância de {@link SigaObjects} a partir do Request do
	 * {@link SwaggerServlet}.
	 * @throws Exception Se houver algo de errado.
	 */
	static SigaObjects getSigaObjects() throws Exception {
		SigaObjects sigaObjects = new SigaObjects(SwaggerServlet.getHttpServletRequest());
		return sigaObjects;
	}
	
	/**
	 * Verifica a presença de um usuário logado e o retorna.
	 * 
	 * @return O login do Usuário na sessão
	 * @throws SwaggerAuthorizationException Se não achar nenhum usuário logado na
	 *                                       sessão.
	 * @see ContextoPersistencia#getUserPrincipal()
	 */
	static String buscarEValidarUsuarioLogado() throws SwaggerAuthorizationException {
		String userPrincipal = ContextoPersistencia.getUserPrincipal();
		if (isNull(userPrincipal)) {
			throw new SwaggerAuthorizationException("Usuário não está logado");
		}

		return userPrincipal;
	}

	/**
	 * Verifica se o usuário tem acesso ao serviço
	 * <code>{@value #DOC_MÓDULO_DE_DOCUMENTOS}<code> e ao serviço informado 
	 * no parâmetro acesso.
	 * 
	 * @param acesso              Caminho do serviço a ser verificado a permissão de acesso
	 * 
	 * @throws Exception Se houver algo de errado.
	 */
	static void assertAcesso(String acesso) throws Exception {
		ApiContext.getSigaObjects().assertAcesso(DOC_MÓDULO_DE_DOCUMENTOS + acesso);
	}

	static void assertAcesso(final ExMobil mob, DpPessoa titular, DpLotacao lotaTitular) throws Exception {
		if (!Ex.getInstance().getComp().podeAcessarDocumento(titular, lotaTitular, mob)) {
			String s = "";
			s += mob.doc().getListaDeAcessosString();
			s = "(" + s + ")";
			s = " " + mob.doc().getExNivelAcessoAtual().getNmNivelAcesso() + " " + s;
	
			Map<ExPapel, List<Object>> mapa = mob.doc().getPerfis();
			boolean isInteressado = false;
	
			for (ExPapel exPapel : mapa.keySet()) {
				Iterator<Object> it = mapa.get(exPapel).iterator();
	
				if ((exPapel != null) && (exPapel.getIdPapel() == ExPapel.PAPEL_INTERESSADO)) {
					while (it.hasNext() && !isInteressado) {
						Object item = it.next();
						isInteressado = item.toString().equals(titular.getSigla()) ? true : false;
					}
				}
	
			}
	
			if (mob.doc().isSemEfeito()) {
				if (!mob.doc().getCadastrante().equals(titular) && !mob.doc().getSubscritor().equals(titular)
						&& !isInteressado) {
					throw new AplicacaoException("Documento " + mob.getSigla() + " cancelado ");
				}
			} else {
				throw new AplicacaoException("Documento " + mob.getSigla() + " inacessível ao usuário "
						+ titular.getSigla() + "/" + lotaTitular.getSiglaCompleta() + "." + s);
			}
		}
	}
	
	private boolean validaAcesso(Annotation[] annotations) throws Exception {
		boolean canAccess = false;
		for (Annotation checkSecurity : annotations) {
			if (checkSecurity.annotationType().isAnnotationPresent(PreAuthorize.class)) {
				PreAuthorize preAuth = checkSecurity.annotationType().getAnnotation(PreAuthorize.class);

		    	Class<?> ClassAnotado = Class.forName(preAuth.securityClass());
		    	Method method = ClassAnotado.getMethod(preAuth.method());
		    	canAccess = (boolean) method.invoke(ClassAnotado.newInstance());
		    	break;
			}
		}
		return canAccess;
	}
	
}